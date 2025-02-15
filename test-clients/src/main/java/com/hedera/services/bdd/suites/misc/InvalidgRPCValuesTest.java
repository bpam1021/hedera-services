package com.hedera.services.bdd.suites.misc;

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
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTopicInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleSign;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOPIC_ID;

public class InvalidgRPCValuesTest extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(InvalidgRPCValuesTest.class);

	public static void main(String... args) throws Exception {
		new InvalidgRPCValuesTest().runSuiteSync();
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
						invalidIdCheck()
				}
		);
	}

	private HapiApiSpec invalidIdCheck() {
		final long MAX_NUM_ALLOWED = 0xFFFFFFFFL;
		final String invalidMaxId = MAX_NUM_ALLOWED + 1 + ".2.3";
		return defaultHapiSpec("TransferWithInvalidAccount")
				.given(
				).when(
				).then(
						// sample queries
						getAccountBalance(invalidMaxId).hasAnswerOnlyPrecheck(INVALID_ACCOUNT_ID),
						getAccountInfo(invalidMaxId).hasCostAnswerPrecheck(INVALID_ACCOUNT_ID),
						getTopicInfo(invalidMaxId).hasCostAnswerPrecheck(INVALID_TOPIC_ID),
						getTokenInfo(invalidMaxId).hasCostAnswerPrecheck(INVALID_TOKEN_ID),

						// sample transactions
						scheduleSign(invalidMaxId).hasKnownStatus(INVALID_SCHEDULE_ID),
						scheduleDelete(invalidMaxId).hasKnownStatus(INVALID_SCHEDULE_ID)
				);
		}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
