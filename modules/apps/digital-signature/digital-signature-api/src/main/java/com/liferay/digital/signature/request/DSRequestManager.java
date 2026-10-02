/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.digital.signature.request;

import com.liferay.digital.signature.model.DSEnvelope;
import com.liferay.digital.signature.model.DSRequest;

import java.util.Collection;
import java.util.Map;

import org.osgi.annotation.versioning.ProviderType;

/**
 * @author Brian I. Kim
 */
@ProviderType
public interface DSRequestManager {

	public DSRequest addDSRequest(
			long companyId, long groupId, long userId, DSEnvelope dsEnvelope,
			long[] fileEntryIds)
		throws Exception;

	public DSRequest fetchDSRequest(long requestId);

	public Map<Long, DSRequest> getDSRequests(
		long companyId, Collection<Long> fileEntryIds);

	public void sendDSRequestNotifications(
		long companyId, long groupId, DSRequest dsRequest);

	public void updateDSRequest(
		long companyId, long groupId, String providerRequestId);

}