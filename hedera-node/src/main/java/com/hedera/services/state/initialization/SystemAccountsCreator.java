package com.hedera.services.state.initialization;

/*-
 * ‌
 * Hedera Services Node
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

import com.hedera.services.ledger.backing.BackingStore;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.Platform;

public interface SystemAccountsCreator {
	/**
	 * Called in {@link com.hedera.services.ServicesMain#init(Platform, NodeId)} to ensure the network
	 * has all expected system accounts, especially when starting from genesis; not really a migration
	 * path, may be better placed in {@code com.hedera.services.ServicesState#internalInit()}.
	 *
	 * @param backingAccounts the ledger accounts
	 * @param addressBook the current address book
	 */
	void ensureSystemAccounts(BackingStore<AccountID, MerkleAccount> backingAccounts, AddressBook addressBook);
}
