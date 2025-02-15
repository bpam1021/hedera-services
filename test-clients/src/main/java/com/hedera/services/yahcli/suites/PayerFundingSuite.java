package com.hedera.services.yahcli.suites;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;

import static com.hedera.services.bdd.spec.HapiApiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.logIt;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.yahcli.commands.validation.ValidationCommand.PAYER;
import static com.hedera.services.yahcli.commands.validation.ValidationCommand.checkBoxed;

public class PayerFundingSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(PayerFundingSuite.class);

	private final long guaranteedBalance;
	private final Map<String, String> specConfig;

	public PayerFundingSuite(long guaranteedBalance, Map<String, String> specConfig) {
		this.guaranteedBalance = guaranteedBalance;
		this.specConfig = specConfig;
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
				fundPayer(),
		});
	}

	private HapiApiSpec fundPayer() {
		return customHapiSpec("FundPayer").withProperties(specConfig)
				.given(
						withOpContext((spec, opLog) -> {
							var subOp = getAccountBalance(PAYER);
							allRunFor(spec, subOp);
							var balance = subOp.getResponse().getCryptogetAccountBalance().getBalance();
							if (balance < guaranteedBalance) {
								var funding = cryptoTransfer(tinyBarsFromTo(
										DEFAULT_PAYER,
										PAYER,
										guaranteedBalance - balance));
								allRunFor(spec, funding);
							}
						})
				).when( ).then(
						logIt(checkBoxed("Payer has at least " + guaranteedBalance + " tℏ"))
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
