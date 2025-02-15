package com.hedera.services.bdd.suites.perf;

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

import com.hedera.services.bdd.suites.HapiApiSuite;

import com.hedera.services.bdd.spec.HapiApiSpec;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;

import static com.hedera.services.bdd.spec.HapiApiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.reduceFeeFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusSubmitMessage;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;


/**
 * This is a suite for any tests with high volume requests of CryptoTransfer and ConsensusSubmitMessage
 * to avoid the potential INSUFFICIENT_PAYER_BALANCE while the test is running.
 */

public class AdjustFeeScheduleSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(AdjustFeeScheduleSuite.class);

	public AdjustFeeScheduleSuite() {
	}

	public static void main(String... args) {
		new AdjustFeeScheduleSuite().runSuiteSync();
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				updateFeesFor()
		);
	}

	private HapiApiSpec updateFeesFor() {
		final var fixedFee = ONE_HUNDRED_HBARS;
		return customHapiSpec("updateFees").withProperties(Map.of(
						"fees.useFixedOffer", "true",
						"fees.fixedOffer", "" + fixedFee
				)
		).given( ).when( ).then(
				reduceFeeFor(CryptoTransfer, 2L, 3L, 3L),
				reduceFeeFor(ConsensusSubmitMessage, 2L, 3L, 3L),
				sleepFor(30000)
		);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

}
