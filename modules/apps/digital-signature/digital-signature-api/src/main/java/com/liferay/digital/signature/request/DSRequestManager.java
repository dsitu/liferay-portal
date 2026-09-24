/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.digital.signature.request;

import com.liferay.digital.signature.model.DSEnvelope;
import com.liferay.digital.signature.model.DSRequest;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.security.permission.PermissionChecker;

import java.util.Collection;
import java.util.List;
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

	public boolean containsPermission(
			PermissionChecker permissionChecker, DSRequest dsRequest,
			String actionId)
		throws PortalException;

	public DSRequest fetchDSRequest(long requestId);

	public DSRequest fetchDSRequest(long companyId, long fileEntryId);

	public Map<Long, DSRequest> getDSRequests(
		long companyId, Collection<Long> fileEntryIds);

	public List<DSRequest> getFileEntryDSRequests(
		long companyId, long fileEntryId);

	public List<DSRequest> getRecipientDSRequests(
		long companyId, long userId, String search, int start, int end);

	public int getRecipientDSRequestsCount(
		long companyId, long userId, String search);

	public List<DSRequest> getSiteDSRequests(
		long companyId, long siteId, String search, int start, int end);

	public int getSiteDSRequestsCount(
		long companyId, long siteId, String search);

	public void sendDSRequestNotifications(
		long companyId, long groupId, DSRequest dsRequest);

	public int sendSignatureReminders(long companyId);

	public void updateDSRequest(
		long companyId, long groupId, String providerRequestId);

	public void voidDSRequest(
		long companyId, long groupId, DSRequest dsRequest, String reason);

}