/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.commerce.digital.signature.internal.util;

import com.liferay.commerce.model.CommerceOrder;
import com.liferay.commerce.model.CommerceOrderAttachment;
import com.liferay.commerce.service.CommerceOrderAttachmentLocalService;
import com.liferay.digital.signature.configuration.DigitalSignatureConfiguration;
import com.liferay.digital.signature.configuration.DigitalSignatureConfigurationUtil;
import com.liferay.digital.signature.model.DSRequest;
import com.liferay.digital.signature.request.DSRequestManager;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.kernel.dao.orm.QueryUtil;
import com.liferay.portal.kernel.module.service.Snapshot;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.HttpComponentsUtil;
import com.liferay.portal.kernel.util.Portal;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Brian I. Kim
 */
public class DSCommerceOrderAttachmentUtil {

	public static String getActionURL(String path, ThemeDisplay themeDisplay) {
		String url = HttpComponentsUtil.addParameter(
			StringBundler.concat(
				themeDisplay.getPortalURL(), Portal.PATH_MODULE, path),
			"backURL", themeDisplay.getURLCurrent());

		return url + "&commerceOrderAttachmentId={id}";
	}

	public static List<CommerceOrderAttachment> getCommerceOrderAttachments(
		CommerceOrder commerceOrder) {

		CommerceOrderAttachmentLocalService
			commerceOrderAttachmentLocalService =
				_commerceOrderAttachmentLocalServiceSnapshot.get();

		return commerceOrderAttachmentLocalService.getCommerceOrderAttachments(
			commerceOrder.getCommerceOrderId(), QueryUtil.ALL_POS,
			QueryUtil.ALL_POS, null);
	}

	public static Map<Long, DSRequest> getDSRequests(
		CommerceOrder commerceOrder, HttpServletRequest httpServletRequest) {

		if (!isEnabled(commerceOrder)) {
			return Collections.emptyMap();
		}

		String key =
			DSCommerceOrderAttachmentUtil.class.getName() +
				commerceOrder.getCommerceOrderId();

		Map<Long, DSRequest> dsRequests =
			(Map<Long, DSRequest>)httpServletRequest.getAttribute(key);

		if (dsRequests != null) {
			return dsRequests;
		}

		Map<Long, Long> fileEntryIdsByCommerceOrderAttachmentId =
			new LinkedHashMap<>();

		for (CommerceOrderAttachment commerceOrderAttachment :
				getCommerceOrderAttachments(commerceOrder)) {

			fileEntryIdsByCommerceOrderAttachmentId.put(
				commerceOrderAttachment.getCommerceOrderAttachmentId(),
				commerceOrderAttachment.getFileEntryId());
		}

		DSRequestManager dsRequestManager = _dsRequestManagerSnapshot.get();

		Map<Long, DSRequest> dsRequestsByFileEntryId =
			dsRequestManager.getDSRequests(
				commerceOrder.getCompanyId(),
				fileEntryIdsByCommerceOrderAttachmentId.values());

		dsRequests = new LinkedHashMap<>();

		for (Map.Entry<Long, Long> entry :
				fileEntryIdsByCommerceOrderAttachmentId.entrySet()) {

			DSRequest dsRequest = dsRequestsByFileEntryId.get(entry.getValue());

			if (dsRequest != null) {
				dsRequests.put(entry.getKey(), dsRequest);
			}
		}

		httpServletRequest.setAttribute(key, dsRequests);

		return dsRequests;
	}

	public static Map<String, String> getSignatureStatuses(
		Map<Long, DSRequest> dsRequests) {

		Map<String, String> signatureStatuses = new HashMap<>();

		for (Map.Entry<Long, DSRequest> entry : dsRequests.entrySet()) {
			DSRequest dsRequest = entry.getValue();

			signatureStatuses.put(
				String.valueOf(entry.getKey()), dsRequest.getStatus());
		}

		return signatureStatuses;
	}

	public static boolean isEnabled(CommerceOrder commerceOrder) {
		DigitalSignatureConfiguration digitalSignatureConfiguration =
			DigitalSignatureConfigurationUtil.getDigitalSignatureConfiguration(
				commerceOrder.getCompanyId(), commerceOrder.getGroupId());

		if (digitalSignatureConfiguration.enabled() &&
			digitalSignatureConfiguration.enableEmbeddedView()) {

			return true;
		}

		return false;
	}

	private static final Snapshot<CommerceOrderAttachmentLocalService>
		_commerceOrderAttachmentLocalServiceSnapshot = new Snapshot<>(
			DSCommerceOrderAttachmentUtil.class,
			CommerceOrderAttachmentLocalService.class);
	private static final Snapshot<DSRequestManager> _dsRequestManagerSnapshot =
		new Snapshot<>(
			DSCommerceOrderAttachmentUtil.class, DSRequestManager.class);

}