package com.hedera.services.yahcli.commands.accounts;

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

import com.hedera.services.yahcli.suites.CreateSuite;
import picocli.CommandLine;

import java.io.File;
import java.util.concurrent.Callable;

import static com.hedera.services.bdd.spec.HapiApiSpec.SpecStatus.PASSED;
import static com.hedera.services.yahcli.config.ConfigUtils.configFrom;
import static com.hedera.services.yahcli.output.CommonMessages.COMMON_MESSAGES;
import static com.hedera.services.yahcli.suites.CreateSuite.NOVELTY;

@CommandLine.Command(
		name = "create",
		subcommands = { CommandLine.HelpCommand.class },
		description = "Creates a new account with a simple Ed25519 key")
public class CreateCommand implements Callable<Integer> {
	private static final int DEFAULT_NUM_RETRIES = 5;

	@CommandLine.ParentCommand
	AccountsCommand accountsCommand;

	@CommandLine.Option(names = { "-m", "--memo" },
			paramLabel = "Memo for new account")
	String memo;

	@CommandLine.Option(names = { "-r", "--retries" },
			paramLabel = "Number of times to retry on BUSY")
	Integer boxedRetries;

	@CommandLine.Option(names = { "-a", "--amount" },
			paramLabel = "<initial balance>",
			description = "how many units of the denomination to use as initial balance",
			defaultValue = "0")
	String amountRepr;

	@CommandLine.Option(names = { "-d", "--denomination" },
			paramLabel = "denomination",
			description = "{ tinybar | hbar | kilobar }",
			defaultValue = "hbar")
	String denomination;

	@Override
	public Integer call() throws Exception {
		final var yahcli = accountsCommand.getYahcli();
		var config = configFrom(yahcli);

		final var noveltyLoc = config.keysLoc() + File.separator + NOVELTY + ".pem";
		final var effectiveMemo = memo != null ? memo : "";
		final var amount = SendCommand.validatedTinybars(yahcli, amountRepr, denomination);
		final var retries = boxedRetries != null ? boxedRetries.intValue() : DEFAULT_NUM_RETRIES;

		final var delegate = new CreateSuite(config.asSpecConfig(), amount, effectiveMemo, noveltyLoc, retries);
		delegate.runSuiteSync();

		if (delegate.getFinalSpecs().get(0).getStatus() == PASSED) {
			COMMON_MESSAGES.info("SUCCESS - account 0.0." + delegate.getCreatedNo().get()
					+ " has been created with balance " + amount + " tinybars " + "and memo '" + effectiveMemo + "'");
		} else {
			COMMON_MESSAGES.warn("FAILED to create a new account with "
					+ amount + " tinybars " + "and memo '" + effectiveMemo + "'");
			return 1;
		}

		return 0;
	}
}
