package com.hedera.test.factories.txns;

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


import com.swirlds.common.system.transaction.SwirldTransaction;

public class PlatformTxnFactory {
	public static SwirldTransaction from(com.hederahashgraph.api.proto.java.Transaction signedTxn) {
		return new SwirldTransaction(signedTxn.toByteArray());
	}

	public static TransactionWithClearFlag withClearFlag(SwirldTransaction txn) {
		return new TransactionWithClearFlag(txn.getContents());
	}

	public static class TransactionWithClearFlag extends SwirldTransaction {
		private boolean hasClearBeenCalled = false;

		public TransactionWithClearFlag(byte[] contents) {
			super(contents);
		}

		@Override
		public void clearSignatures() {
			hasClearBeenCalled = true;
			super.clearSignatures();
		}

		public boolean hasClearBeenCalled() {
			return hasClearBeenCalled;
		}
	}
}
