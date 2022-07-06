package com.hedera.services.bdd.suites.contract.hapi;

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

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiPropertySource.asHexedSolidityAddress;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.isLiteralResult;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.KeyFactory.KeyType.THRESHOLD;
import static com.hedera.services.bdd.spec.keys.SigControl.SECP256K1_ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocalWithFunctionAbi;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getReceipt;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCallWithFunctionAbi;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromToWithAlias;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION;
import static com.hedera.services.bdd.suites.contract.Utils.getABIFor;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class ContractCallLocalSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ContractCallLocalSuite.class);
	private static final String CONTRACT = "CreateTrivial";
	private static final String TOKEN = "TestToken";
	private static final String SYMBOL = "ħT";
	private static final int DECIMALS = 13;

	public static void main(String... args) {
		new ContractCallLocalSuite().runSuiteSync();
	}

	@Override
	public boolean canRunConcurrent() {
		return true;
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
//						deletedContract(),
//						invalidContractID(),
//						impureCallFails(),
//						insufficientFeeFails(),
//						lowBalanceFails(),
//						erc20Query(),
//						vanillaSuccess()
						erc20Approve(),
				}
		);
	}

	private HapiApiSpec vanillaSuccess() {
		return defaultHapiSpec("VanillaSuccess")
				.given(
						uploadInitCode(CONTRACT),
						contractCreate(CONTRACT).adminKey(THRESHOLD)
				).when(
						contractCall(CONTRACT, "create").gas(785_000)
				).then(
						sleepFor(3_000L),
						contractCallLocal(CONTRACT, "getIndirect")
								.has(resultWith().resultViaFunctionName("getIndirect", CONTRACT,
										isLiteralResult(new Object[] { BigInteger.valueOf(7L) })))
				);
	}

	private HapiApiSpec impureCallFails() {
		return defaultHapiSpec("ImpureCallFails")
				.given(
						uploadInitCode(CONTRACT),
						contractCreate(CONTRACT).adminKey(THRESHOLD)
				).when().then(
						sleepFor(3_000L),
						contractCallLocal(CONTRACT, "create")
								.nodePayment(1_234_567)
								.hasAnswerOnlyPrecheck(ResponseCodeEnum.LOCAL_CALL_MODIFICATION_EXCEPTION)
				);
	}

	private HapiApiSpec deletedContract() {
		return defaultHapiSpec("InvalidDeletedContract")
				.given(
						uploadInitCode(CONTRACT),
						contractCreate(CONTRACT)
				).when(
						contractDelete(CONTRACT)
				).then(
						contractCallLocal(CONTRACT, "create")
								.nodePayment(1_234_567)
								.hasAnswerOnlyPrecheck(CONTRACT_DELETED)
				);
	}

	private HapiApiSpec invalidContractID() {
		final var invalidContract = HapiSpecSetup.getDefaultInstance().invalidContractName();
		final var functionAbi = getABIFor(FUNCTION, "getIndirect", "CreateTrivial");
		return defaultHapiSpec("InvalidContractID")
				.given(
				)
				.when(
				)
				.then(
						contractCallLocalWithFunctionAbi(invalidContract, functionAbi)
								.nodePayment(1_234_567)
								.hasAnswerOnlyPrecheck(INVALID_CONTRACT_ID),
						contractCallLocalWithFunctionAbi("0.0.0", functionAbi)
								.nodePayment(1_234_567)
								.hasAnswerOnlyPrecheck(INVALID_CONTRACT_ID)
				);
	}

	private HapiApiSpec insufficientFeeFails() {
		final long adequateQueryPayment = 500_000L;

		return defaultHapiSpec("InsufficientFee")
				.given(
						cryptoCreate("payer"),
						uploadInitCode(CONTRACT),
						contractCreate(CONTRACT)
				).when(
						contractCall(CONTRACT, "create").gas(785_000)
				).then(
						sleepFor(3_000L),
						contractCallLocal(CONTRACT, "getIndirect")
								.nodePayment(adequateQueryPayment)
								.fee(0L)
								.payingWith("payer")
								.hasAnswerOnlyPrecheck(INSUFFICIENT_TX_FEE));
	}

	private HapiApiSpec lowBalanceFails() {
		final long adequateQueryPayment = 500_000_000L;

		return defaultHapiSpec("LowBalanceFails")
				.given(
						cryptoCreate("payer"),
						cryptoCreate("payer").balance(adequateQueryPayment),
						uploadInitCode(CONTRACT),
						contractCreate(CONTRACT)
				).when(
						contractCall(CONTRACT, "create").gas(785_000)
				).then(
						sleepFor(3_000L),
						contractCallLocal(CONTRACT, "getIndirect")
								.logged()
								.payingWith("payer")
								.nodePayment(adequateQueryPayment)
								.hasAnswerOnlyPrecheck(INSUFFICIENT_PAYER_BALANCE),
						getAccountBalance("payer").logged(),
						sleepFor(1_000L),
						getAccountBalance("payer").logged()
				);
	}

	private HapiApiSpec erc20Query() {
		final var decimalsABI = "{\"constant\": true,\"inputs\": [],\"name\": \"decimals\"," +
				"\"outputs\": [{\"name\": \"\",\"type\": \"uint8\"}],\"payable\": false," +
				"\"type\": \"function\"},";
		final var creation = "autoCreation";
		final var eoaSender = "eoaSender";
		final var ecdsaKey = "ecdsaKey";
		final var nonStaticDecimalsCall = "nonStaticDecimalsCall";

		return defaultHapiSpec("erc20Queries")
				.given(
						newKeyNamed(ecdsaKey).shape(SECP256K1_ON),
						cryptoTransfer(
								tinyBarsFromToWithAlias(DEFAULT_PAYER, ecdsaKey, ONE_HUNDRED_HBARS)
						).via(creation),
						getReceipt(creation).savingAutoCreation(eoaSender, ecdsaKey),
						tokenCreate(TOKEN).decimals(DECIMALS).symbol(SYMBOL).asCallableContract()
				).when(
//						contractCallLocalWithFunctionAbi(TOKEN, decimalsABI)
//								.has(resultWith().resultThruAbi(decimalsABI,
//										isLiteralResult(new Object[] { BigInteger.valueOf(DECIMALS) })))
				).then(
						contractCallWithFunctionAbi(TOKEN, decimalsABI)
								.payingWith(eoaSender)
								.via(nonStaticDecimalsCall),
						getTxnRecord(nonStaticDecimalsCall).logged()
				);
	}

	private HapiApiSpec erc20Approve() {
		final var approveAbi = "{ \"constant\": false, \"inputs\": [ { \"name\": \"_spender\", \"type\": \"address\" " +
				"}," +
				" { \"name\": \"_value\", \"type\": \"uint256\" } ], \"name\": \"approve\", \"outputs\": [ { \"name\":" +
				" " +
				"\"\", \"type\": \"bool\" } ], \"payable\": false, \"stateMutability\": \"nonpayable\", \"type\": " +
				"\"function\" }";
		final var creation = "autoCreation";
		final var spender = "spender";
		final var eoaSender = "eoaSender";
		final var ecdsaKey = "ecdsaKey";
		final var nonStaticDecimalsCall = "nonStaticDecimalsCall";
		final AtomicReference<String> spenderMirrorAddr = new AtomicReference<>();

		return defaultHapiSpec("Erc20Approve")
				.given(
						cryptoCreate(spender).exposingCreatedIdTo(id ->
								spenderMirrorAddr.set(asHexedSolidityAddress(id))),
						newKeyNamed(ecdsaKey).shape(SECP256K1_ON),
						cryptoTransfer(
								tinyBarsFromToWithAlias(DEFAULT_PAYER, ecdsaKey, ONE_HUNDRED_HBARS)
						).via(creation),
						getReceipt(creation).savingAutoCreation(eoaSender, ecdsaKey),
						tokenCreate(TOKEN)
								.treasury(eoaSender)
								.initialSupply(1000)
								.decimals(DECIMALS)
								.symbol(SYMBOL)
								.asCallableContract()
				).when(
						sourcing(() -> contractCallWithFunctionAbi(TOKEN, approveAbi, spenderMirrorAddr.get(), 100)
								.gas(1_000_000)
								.payingWith(eoaSender)
								.via(nonStaticDecimalsCall))
				).then(
						/* SAMPLE OUTPUT:
						      ...
							  contractCallResult {
								contractID {
								   contractNum: 359
								}
								contractCallResult: "\000\000...\000\017"
								...
								sender_id {
								   shardNum: -70672035
								   realmNum: 8062295507940796406
								   accountNum: 4428982008424832196
								}
							  }
						*/
						getTxnRecord(nonStaticDecimalsCall)
								.andAllChildRecords()
								.logged()
								.hasChildRecords(recordWith().status(SUCCESS))
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
