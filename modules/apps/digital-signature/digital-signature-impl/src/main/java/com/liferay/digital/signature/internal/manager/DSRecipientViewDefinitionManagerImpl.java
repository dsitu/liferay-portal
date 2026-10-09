/**
 * SPDX-FileCopyrightText: (c) 2000 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.digital.signature.internal.manager;

import com.liferay.digital.signature.configuration.DigitalSignatureConfiguration;
import com.liferay.digital.signature.configuration.DigitalSignatureConfigurationUtil;
import com.liferay.digital.signature.configuration.DigitalSignatureSystemConfigurationUtil;
import com.liferay.digital.signature.internal.http.DSHttp;
import com.liferay.digital.signature.manager.DSRecipientViewDefinitionManager;
import com.liferay.digital.signature.model.DSRecipientViewDefinition;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.security.auth.PrincipalException;
import com.liferay.portal.kernel.security.permission.PermissionChecker;
import com.liferay.portal.kernel.security.permission.PermissionThreadLocal;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.portal.kernel.util.StringUtil;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author José Abelenda
 */
@Component(service = DSRecipientViewDefinitionManager.class)
public class DSRecipientViewDefinitionManagerImpl
	implements DSRecipientViewDefinitionManager {

	@Override
	public String addDSRecipientViewDefinition(
			long companyId, long groupId, long userId, String dsEnvelopeId,
			String portalURL)
		throws Exception {

		DigitalSignatureConfiguration digitalSignatureConfiguration =
			DigitalSignatureConfigurationUtil.getDigitalSignatureConfiguration(
				companyId, groupId);

		String appOriginURL =
			DigitalSignatureSystemConfigurationUtil.getAppOriginURL(
				digitalSignatureConfiguration.environment());

		User user = _userLocalService.getUser(userId);

		DSRecipientViewDefinition dsRecipientViewDefinition =
			new DSRecipientViewDefinition();

		dsRecipientViewDefinition.setAuthenticationMethod("none");
		dsRecipientViewDefinition.setDSClientUserId(String.valueOf(userId));
		dsRecipientViewDefinition.setEmailAddress(user.getEmailAddress());
		dsRecipientViewDefinition.setFrameAncestors(
			new String[] {portalURL, appOriginURL});
		dsRecipientViewDefinition.setMessageOrigins(
			new String[] {appOriginURL});
		dsRecipientViewDefinition.setReturnURL(appOriginURL);
		dsRecipientViewDefinition.setUserName(user.getFullName());

		return addDSRecipientViewDefinition(
			companyId, groupId, dsEnvelopeId, dsRecipientViewDefinition);
	}

	@Override
	public String addDSRecipientViewDefinition(
			long companyId, long groupId, String dsEnvelopeId,
			DSRecipientViewDefinition dsRecipientViewDefinition)
		throws Exception {

		_checkPermission(companyId, dsRecipientViewDefinition);

		JSONObject jsonObject = _dsHttp.post(
			companyId, groupId,
			StringBundler.concat(
				"envelopes/", dsEnvelopeId, "/views/recipient"),
			dsRecipientViewDefinition.toJSONObject());

		return jsonObject.getString("url");
	}

	private void _checkPermission(
			long companyId, DSRecipientViewDefinition dsRecipientViewDefinition)
		throws Exception {

		PermissionChecker permissionChecker =
			PermissionThreadLocal.getPermissionChecker();

		if (permissionChecker == null) {
			throw new PrincipalException.MustBeCompanyAdmin(permissionChecker);
		}

		if (permissionChecker.isCompanyAdmin(companyId)) {
			return;
		}

		User user = permissionChecker.getUser();

		if ((user != null) &&
			StringUtil.equalsIgnoreCase(
				user.getEmailAddress(),
				dsRecipientViewDefinition.getEmailAddress())) {

			return;
		}

		throw new PrincipalException.MustBeCompanyAdmin(permissionChecker);
	}

	@Reference
	private DSHttp _dsHttp;

	@Reference
	private UserLocalService _userLocalService;

}