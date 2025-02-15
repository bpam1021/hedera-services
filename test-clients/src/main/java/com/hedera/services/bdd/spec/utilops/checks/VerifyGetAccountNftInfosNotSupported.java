package com.hedera.services.bdd.spec.utilops.checks;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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
import com.hedera.services.bdd.spec.utilops.UtilOp;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.TokenGetAccountNftInfosQuery;
import org.junit.jupiter.api.Assertions;

import static com.hedera.services.bdd.spec.HapiPropertySource.asAccount;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;

public class VerifyGetAccountNftInfosNotSupported extends UtilOp {
	@Override
	protected boolean submitOp(HapiApiSpec spec) throws Throwable {
		TokenGetAccountNftInfosQuery.Builder op = TokenGetAccountNftInfosQuery.newBuilder()
				.setAccountID(asAccount("0.0.2"));
		Query query = Query.newBuilder().setTokenGetAccountNftInfos(op).build();
		Response response = spec.clients().getTokenSvcStub(targetNodeFor(spec), useTls).getAccountNftInfos(query);
		Assertions.assertEquals(
				NOT_SUPPORTED,
				response.getTokenGetAccountNftInfos().getHeader().getNodeTransactionPrecheckCode());
		return false;
	}
}
