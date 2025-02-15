package com.hedera.services.bdd.spec.transactions.token;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.google.common.base.MoreObjects;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.fees.AdapterUtils;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.usage.BaseTransactionMeta;
import com.hedera.services.usage.state.UsageAccumulator;
import com.hedera.services.usage.token.TokenOpsUsage;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.TokenWipeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.fee.SigValueObj;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.hedera.services.bdd.spec.transactions.TxnUtils.suFrom;
import static com.hedera.services.usage.token.TokenOpsUsageUtils.TOKEN_OPS_USAGE_UTILS;

public class HapiTokenWipe extends HapiTxnOp<HapiTokenWipe> {
	static final Logger log = LogManager.getLogger(HapiTokenWipe.class);

	private String account;
	private String token;
	private long amount;
	private List<Long> serialNumbers;
	private SubType subType;

	@Override
	public HederaFunctionality type() {
		return HederaFunctionality.TokenAccountWipe;
	}

	public HapiTokenWipe(String token, String account, long amount) {
		this.token = token;
		this.account = account;
		this.amount = amount;
		this.serialNumbers = new ArrayList<>();
		this.subType = SubType.TOKEN_FUNGIBLE_COMMON;
	}

	public HapiTokenWipe(String token, String account, List<Long> serialNumbers) {
		this.token = token;
		this.account = account;
		this.serialNumbers = serialNumbers;
		this.subType = SubType.TOKEN_NON_FUNGIBLE_UNIQUE;
	}

	@Override
	protected HapiTokenWipe self() {
		return this;
	}

	@Override
	protected long feeFor(HapiApiSpec spec, Transaction txn, int numPayerKeys) throws Throwable {
		return spec.fees().forActivityBasedOp(
				HederaFunctionality.TokenAccountWipe, subType, this::usageEstimate, txn, numPayerKeys);
	}

	private FeeData usageEstimate(TransactionBody txn, SigValueObj svo) {
		UsageAccumulator accumulator = new UsageAccumulator();
		final var tokenWipeMeta = TOKEN_OPS_USAGE_UTILS.tokenWipeUsageFrom(txn, subType);
		final var baseTransactionMeta = new BaseTransactionMeta(txn.getMemoBytes().size(), 0);
		TokenOpsUsage tokenOpsUsage = new TokenOpsUsage();
		tokenOpsUsage.tokenWipeUsage(suFrom(svo), baseTransactionMeta, tokenWipeMeta, accumulator );
		return AdapterUtils.feeDataFrom(accumulator);
	}

	@Override
	protected Consumer<TransactionBody.Builder> opBodyDef(HapiApiSpec spec) throws Throwable {
		var tId = TxnUtils.asTokenId(token, spec);
		var aId = TxnUtils.asId(account, spec);
		TokenWipeAccountTransactionBody opBody = spec
				.txns()
				.<TokenWipeAccountTransactionBody, TokenWipeAccountTransactionBody.Builder>body(
						TokenWipeAccountTransactionBody.class, b -> {
							b.setToken(tId);
							b.setAccount(aId);
							b.setAmount(amount);
							b.addAllSerialNumbers(serialNumbers);
						});
		return b -> b.setTokenWipe(opBody);
	}

	@Override
	protected List<Function<HapiApiSpec, Key>> defaultSigners() {
		return List.of(
				spec -> spec.registry().getKey(effectivePayer(spec)),
				spec -> spec.registry().getWipeKey(token));
	}

	@Override
	protected Function<Transaction, TransactionResponse> callToUse(HapiApiSpec spec) {
		return spec.clients().getTokenSvcStub(targetNodeFor(spec), useTls)::wipeTokenAccount;
	}

	@Override
	protected void updateStateOf(HapiApiSpec spec) {
	}

	@Override
	protected MoreObjects.ToStringHelper toStringHelper() {
		MoreObjects.ToStringHelper helper = super.toStringHelper()
				.add("token", token)
				.add("account", account)
				.add("amount", amount)
				.add("serialNumbers", serialNumbers);
		return helper;
	}
}
