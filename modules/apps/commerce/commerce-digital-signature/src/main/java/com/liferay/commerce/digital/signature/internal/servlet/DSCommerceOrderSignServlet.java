/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.commerce.digital.signature.internal.servlet;

import com.liferay.commerce.model.CommerceOrderAttachment;
import com.liferay.commerce.service.CommerceOrderAttachmentLocalService;
import com.liferay.digital.signature.constants.DigitalSignaturePortletKeys;
import com.liferay.digital.signature.model.DSRequest;
import com.liferay.digital.signature.request.DSRequestManager;
import com.liferay.digital.signature.url.SignDSURLProvider;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.util.HttpComponentsUtil;
import com.liferay.portal.kernel.util.ParamUtil;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.Validator;

import jakarta.servlet.Servlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Brian I. Kim
 */
@Component(
	property = {
		"osgi.http.whiteboard.context.path=/commerce-digital-signature",
		"osgi.http.whiteboard.servlet.name=com.liferay.commerce.digital.signature.internal.servlet.DSCommerceOrderSignServlet",
		"osgi.http.whiteboard.servlet.pattern=/commerce-digital-signature/sign/*"
	},
	service = Servlet.class
)
public class DSCommerceOrderSignServlet extends HttpServlet {

	@Override
	protected void doGet(
			HttpServletRequest httpServletRequest,
			HttpServletResponse httpServletResponse)
		throws IOException {

		String backURL = _portal.escapeRedirect(
			ParamUtil.getString(httpServletRequest, "backURL"));

		if (Validator.isNull(backURL)) {
			backURL = _portal.getPortalURL(httpServletRequest);
		}

		try {
			CommerceOrderAttachment commerceOrderAttachment =
				_commerceOrderAttachmentLocalService.
					fetchCommerceOrderAttachment(
						ParamUtil.getLong(
							httpServletRequest, "commerceOrderAttachmentId"));

			long companyId = _portal.getCompanyId(httpServletRequest);

			User user = _portal.getUser(httpServletRequest);

			DSRequest dsRequest = null;

			if (commerceOrderAttachment != null) {
				dsRequest = _dsRequestManager.fetchDSRequest(
					companyId, commerceOrderAttachment.getFileEntryId());
			}

			if ((user == null) || (dsRequest == null) ||
				!dsRequest.isSignatureRequired(user.getEmailAddress())) {

				httpServletResponse.sendRedirect(backURL);

				return;
			}

			String portletNamespace = _portal.getPortletNamespace(
				DigitalSignaturePortletKeys.SIGN_DIGITAL_SIGNATURE);

			httpServletResponse.sendRedirect(
				HttpComponentsUtil.addParameter(
					_signDSURLProvider.getURL(
						companyId, dsRequest.getSiteId(),
						dsRequest.getDSRequestId()),
					portletNamespace + "backURL", backURL));
		}
		catch (Exception exception) {
			throw new IOException(exception);
		}
	}

	@Reference
	private CommerceOrderAttachmentLocalService
		_commerceOrderAttachmentLocalService;

	@Reference
	private DSRequestManager _dsRequestManager;

	@Reference
	private Portal _portal;

	@Reference
	private SignDSURLProvider _signDSURLProvider;

}