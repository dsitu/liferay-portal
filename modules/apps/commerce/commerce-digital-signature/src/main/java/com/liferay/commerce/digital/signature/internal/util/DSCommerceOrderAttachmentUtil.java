/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.commerce.digital.signature.internal.util;

import com.liferay.account.model.AccountEntryUserRel;
import com.liferay.account.service.AccountEntryUserRelLocalService;
import com.liferay.commerce.model.CommerceOrder;
import com.liferay.commerce.model.CommerceOrderAttachment;
import com.liferay.commerce.product.model.CommerceChannel;
import com.liferay.commerce.product.service.CommerceChannelLocalService;
import com.liferay.commerce.service.CommerceOrderAttachmentLocalService;
import com.liferay.digital.signature.configuration.DigitalSignatureConfiguration;
import com.liferay.digital.signature.configuration.DigitalSignatureConfigurationUtil;
import com.liferay.digital.signature.manager.DSRequestManager;
import com.liferay.digital.signature.model.DSRequest;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.kernel.dao.orm.QueryUtil;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.json.JSONUtil;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.module.service.Snapshot;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.HttpComponentsUtil;
import com.liferay.portal.kernel.util.Portal;

import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Brian I. Kim
 */
public class DSCommerceOrderAttachmentUtil {

	public static List<User> getAccountUsers(CommerceOrder commerceOrder) {
		List<User> users = new ArrayList<>();

		AccountEntryUserRelLocalService accountEntryUserRelLocalService =
			_accountEntryUserRelLocalServiceSnapshot.get();

		UserLocalService userLocalService = _userLocalServiceSnapshot.get();

		for (AccountEntryUserRel accountEntryUserRel :
				accountEntryUserRelLocalService.
					getAccountEntryUserRelsByAccountEntryId(
						commerceOrder.getCommerceAccountId())) {

			User user = userLocalService.fetchUser(
				accountEntryUserRel.getAccountUserId());

			if ((user != null) && user.isActive()) {
				users.add(user);
			}
		}

		return users;
	}

	public static String getActionURL(String path, ThemeDisplay themeDisplay) {
		String url = HttpComponentsUtil.addParameter(
			StringBundler.concat(
				themeDisplay.getPortalURL(), Portal.PATH_MODULE, path),
			"backURL", themeDisplay.getURLCurrent());

		return url + "&commerceOrderAttachmentId={id}";
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

		Map<Long, Long> fileEntryIdsMap = new LinkedHashMap<>();

		CommerceOrderAttachmentLocalService
			commerceOrderAttachmentLocalService =
				_commerceOrderAttachmentLocalServiceSnapshot.get();

		for (CommerceOrderAttachment commerceOrderAttachment :
				commerceOrderAttachmentLocalService.getCommerceOrderAttachments(
					commerceOrder.getCommerceOrderId(), QueryUtil.ALL_POS,
					QueryUtil.ALL_POS, null)) {

			fileEntryIdsMap.put(
				commerceOrderAttachment.getCommerceOrderAttachmentId(),
				commerceOrderAttachment.getFileEntryId());
		}

		DSRequestManager dsRequestManager = _dsRequestManagerSnapshot.get();

		Map<Long, DSRequest> fileEntryDSRequests =
			dsRequestManager.getDSRequests(
				commerceOrder.getCompanyId(), fileEntryIdsMap.values());

		dsRequests = new LinkedHashMap<>();

		for (Map.Entry<Long, Long> entry : fileEntryIdsMap.entrySet()) {
			DSRequest dsRequest = fileEntryDSRequests.get(entry.getValue());

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

	public static long getSiteGroupId(CommerceOrder commerceOrder) {
		CommerceChannelLocalService commerceChannelLocalService =
			_commerceChannelLocalServiceSnapshot.get();

		CommerceChannel commerceChannel =
			commerceChannelLocalService.fetchCommerceChannelByGroupClassPK(
				commerceOrder.getGroupId());

		if (commerceChannel == null) {
			return 0;
		}

		return commerceChannel.getSiteGroupId();
	}

	public static JSONObject getUserJSONObject(User user) {
		return JSONUtil.put(
			"emailAddress", user.getEmailAddress()
		).put(
			"name", user.getFullName()
		).put(
			"userId", user.getUserId()
		);
	}

	public static boolean isEnabled(CommerceOrder commerceOrder) {
		DigitalSignatureConfiguration digitalSignatureConfiguration =
			DigitalSignatureConfigurationUtil.getDigitalSignatureConfiguration(
				commerceOrder.getCompanyId(), getSiteGroupId(commerceOrder));

		if (digitalSignatureConfiguration.embeddedViewEnabled() &&
			digitalSignatureConfiguration.enabled()) {

			return true;
		}

		return false;
	}

	private static final Snapshot<AccountEntryUserRelLocalService>
		_accountEntryUserRelLocalServiceSnapshot = new Snapshot<>(
			DSCommerceOrderAttachmentUtil.class,
			AccountEntryUserRelLocalService.class);
	private static final Snapshot<CommerceChannelLocalService>
		_commerceChannelLocalServiceSnapshot = new Snapshot<>(
			DSCommerceOrderAttachmentUtil.class,
			CommerceChannelLocalService.class);
	private static final Snapshot<CommerceOrderAttachmentLocalService>
		_commerceOrderAttachmentLocalServiceSnapshot = new Snapshot<>(
			DSCommerceOrderAttachmentUtil.class,
			CommerceOrderAttachmentLocalService.class);
	private static final Snapshot<DSRequestManager> _dsRequestManagerSnapshot =
		new Snapshot<>(
			DSCommerceOrderAttachmentUtil.class, DSRequestManager.class);
	private static final Snapshot<UserLocalService> _userLocalServiceSnapshot =
		new Snapshot<>(
			DSCommerceOrderAttachmentUtil.class, UserLocalService.class);

}