/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.digital.signature.web.internal.portlet.action;

import com.liferay.digital.signature.configuration.DigitalSignatureConfiguration;
import com.liferay.digital.signature.configuration.DigitalSignatureConfigurationUtil;
import com.liferay.digital.signature.constants.DigitalSignaturePortletKeys;
import com.liferay.digital.signature.manager.DSRecipientViewDefinitionManager;
import com.liferay.digital.signature.manager.DSRequestManager;
import com.liferay.digital.signature.model.DSRequest;
import com.liferay.digital.signature.web.internal.display.context.SignDSRequestDisplayContext;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.portlet.bridges.mvc.MVCRenderCommand;
import com.liferay.portal.kernel.security.permission.ActionKeys;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.ParamUtil;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.WebKeys;

import jakarta.portlet.PortletException;
import jakarta.portlet.RenderRequest;
import jakarta.portlet.RenderResponse;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Danny Situ
 */
@Component(
	property = {
		"jakarta.portlet.name=" + DigitalSignaturePortletKeys.SIGN_DIGITAL_SIGNATURE,
		"mvc.command.name=/digital_signature/sign_ds_request",
		"portlet.add.default.resource.check.whitelist.mvc.action=true"
	},
	service = MVCRenderCommand.class
)
public class SignDSRequestMVCRenderCommand implements MVCRenderCommand {

	@Override
	public String render(
			RenderRequest renderRequest, RenderResponse renderResponse)
		throws PortletException {

		ThemeDisplay themeDisplay = (ThemeDisplay)renderRequest.getAttribute(
			WebKeys.THEME_DISPLAY);

		DSRequest dsRequest = _dsRequestManager.fetchDSRequest(
			ParamUtil.getLong(renderRequest, "dsRequestId"));

		if ((dsRequest == null) ||
			(dsRequest.getCompanyId() != themeDisplay.getCompanyId())) {

			return "/sign_digital_signature/error.jsp";
		}

		try {
			if (!_dsRequestManager.hasPermission(
					themeDisplay.getPermissionChecker(), dsRequest,
					ActionKeys.VIEW)) {

				return "/sign_digital_signature/error.jsp";
			}
		}
		catch (PortalException portalException) {
			throw new PortletException(portalException);
		}

		DigitalSignatureConfiguration digitalSignatureConfiguration =
			DigitalSignatureConfigurationUtil.getDigitalSignatureConfiguration(
				themeDisplay.getCompanyId(), dsRequest.getSiteGroupId());

		if (!digitalSignatureConfiguration.enabled() ||
			!digitalSignatureConfiguration.enableEmbeddedView()) {

			return "/sign_digital_signature/error.jsp";
		}

		_dsRequestManager.updateDSRequest(
			themeDisplay.getCompanyId(), dsRequest.getSiteGroupId(),
			dsRequest.getProviderRequestId());

		dsRequest = _dsRequestManager.fetchDSRequest(
			dsRequest.getDSRequestId());

		User user = themeDisplay.getUser();

		if ((dsRequest == null) ||
			!dsRequest.isSignatureRequired(user.getEmailAddress())) {

			return "/sign_digital_signature/error.jsp";
		}

		String portalURL = _portal.getPortalURL(
			_portal.getHttpServletRequest(renderRequest));

		try {
			renderRequest.setAttribute(
				WebKeys.PORTLET_DISPLAY_CONTEXT,
				new SignDSRequestDisplayContext(
					digitalSignatureConfiguration, dsRequest, renderRequest,
					renderResponse,
					_dsRecipientViewDefinitionManager.
						addDSRecipientViewDefinition(
							themeDisplay.getCompanyId(),
							dsRequest.getSiteGroupId(),
							themeDisplay.getUserId(),
							dsRequest.getProviderRequestId(), portalURL)));
		}
		catch (Exception exception) {
			_log.error(
				"Unable to create the signing view for signature request " +
					dsRequest.getDSRequestId(),
				exception);

			return "/sign_digital_signature/error.jsp";
		}

		return "/sign_digital_signature/sign_ds_request.jsp";
	}

	private static final Log _log = LogFactoryUtil.getLog(
		SignDSRequestMVCRenderCommand.class);

	@Reference
	private DSRecipientViewDefinitionManager _dsRecipientViewDefinitionManager;

	@Reference
	private DSRequestManager _dsRequestManager;

	@Reference
	private Portal _portal;

}