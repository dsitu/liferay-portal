/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.commerce.digital.signature.internal.servlet;

import com.liferay.commerce.model.CommerceOrderAttachment;
import com.liferay.commerce.service.CommerceOrderAttachmentService;
import com.liferay.digital.signature.model.DSRequest;
import com.liferay.digital.signature.model.DSRequestRecipient;
import com.liferay.digital.signature.request.DSRequestManager;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.json.JSONUtil;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.security.auth.PrincipalThreadLocal;
import com.liferay.portal.kernel.security.permission.PermissionChecker;
import com.liferay.portal.kernel.security.permission.PermissionCheckerFactory;
import com.liferay.portal.kernel.security.permission.PermissionThreadLocal;
import com.liferay.portal.kernel.util.ContentTypes;
import com.liferay.portal.kernel.util.ParamUtil;
import com.liferay.portal.kernel.util.Portal;

import jakarta.servlet.Servlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;

import java.util.Date;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Brian I. Kim
 */
@Component(
	property = {
		"osgi.http.whiteboard.context.path=/commerce-digital-signature",
		"osgi.http.whiteboard.servlet.name=com.liferay.commerce.digital.signature.internal.servlet.DSCommerceOrderSignatureStatusServlet",
		"osgi.http.whiteboard.servlet.pattern=/commerce-digital-signature/signature-status/*"
	},
	service = Servlet.class
)
public class DSCommerceOrderSignatureStatusServlet extends HttpServlet {

	@Override
	protected void doGet(
			HttpServletRequest httpServletRequest,
			HttpServletResponse httpServletResponse)
		throws IOException {

		PermissionChecker permissionChecker =
			PermissionThreadLocal.getPermissionChecker();
		String name = PrincipalThreadLocal.getName();

		try {
			User user = _portal.getUser(httpServletRequest);

			if (user == null) {
				return;
			}

			PrincipalThreadLocal.setName(user.getUserId());
			PermissionThreadLocal.setPermissionChecker(
				_permissionCheckerFactory.create(user));

			CommerceOrderAttachment commerceOrderAttachment =
				_commerceOrderAttachmentService.fetchCommerceOrderAttachment(
					ParamUtil.getLong(
						httpServletRequest, "commerceOrderAttachmentId"));

			httpServletResponse.setContentType(ContentTypes.APPLICATION_JSON);

			PrintWriter printWriter = httpServletResponse.getWriter();

			if (commerceOrderAttachment == null) {
				printWriter.write("{}");

				return;
			}

			printWriter.write(
				_getJSONString(
					_dsRequestManager.fetchDSRequest(
						user.getCompanyId(),
						commerceOrderAttachment.getFileEntryId())));
		}
		catch (Exception exception) {
			throw new IOException(exception);
		}
		finally {
			PermissionThreadLocal.setPermissionChecker(permissionChecker);
			PrincipalThreadLocal.setName(name);
		}
	}

	private String _getJSONString(DSRequest dsRequest) throws Exception {
		if (dsRequest == null) {
			return "{}";
		}

		return JSONUtil.put(
			"expirationDate", _toTime(dsRequest.getExpirationDate())
		).put(
			"providerRequestId", dsRequest.getProviderRequestId()
		).put(
			"recipients",
			JSONUtil.toJSONArray(
				dsRequest.getDSRequestRecipients(),
				this::_getRecipientJSONObject)
		).put(
			"requesterEmailAddress", dsRequest.getRequesterEmailAddress()
		).put(
			"requesterName", dsRequest.getRequesterName()
		).put(
			"requestStatus", dsRequest.getStatus()
		).put(
			"statusDate", _toTime(dsRequest.getStatusDate())
		).toString();
	}

	private JSONObject _getRecipientJSONObject(
		DSRequestRecipient dsRequestRecipient) {

		return JSONUtil.put(
			"emailAddress", dsRequestRecipient.getEmailAddress()
		).put(
			"name", dsRequestRecipient.getName()
		).put(
			"requestRecipientStatus", dsRequestRecipient.getStatus()
		).put(
			"sentDate", _toTime(dsRequestRecipient.getSentDate())
		).put(
			"signingOrder", dsRequestRecipient.getSigningOrder()
		).put(
			"statusDate", _toTime(dsRequestRecipient.getStatusDate())
		);
	}

	private Long _toTime(Date date) {
		if (date == null) {
			return null;
		}

		return date.getTime();
	}

	@Reference
	private CommerceOrderAttachmentService _commerceOrderAttachmentService;

	@Reference
	private DSRequestManager _dsRequestManager;

	@Reference
	private PermissionCheckerFactory _permissionCheckerFactory;

	@Reference
	private Portal _portal;

}