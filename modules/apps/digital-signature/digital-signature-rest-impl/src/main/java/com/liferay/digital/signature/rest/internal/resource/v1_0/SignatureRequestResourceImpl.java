/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.digital.signature.rest.internal.resource.v1_0;

import com.liferay.digital.signature.constants.DSRequestConstants;
import com.liferay.digital.signature.manager.DSRequestManager;
import com.liferay.digital.signature.model.DSEnvelope;
import com.liferay.digital.signature.model.DSRecipient;
import com.liferay.digital.signature.model.DSRequest;
import com.liferay.digital.signature.rest.dto.v1_0.SignatureRequest;
import com.liferay.digital.signature.rest.dto.v1_0.SignatureRequestRecipient;
import com.liferay.digital.signature.rest.resource.v1_0.SignatureRequestResource;
import com.liferay.digital.signature.url.SignDSURLProvider;
import com.liferay.document.library.kernel.service.DLAppService;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.kernel.exception.NoSuchUserException;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.security.auth.PrincipalException;
import com.liferay.portal.kernel.security.permission.ActionKeys;
import com.liferay.portal.kernel.security.permission.PermissionThreadLocal;
import com.liferay.portal.kernel.service.GroupService;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.portal.kernel.util.ArrayUtil;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.vulcan.dto.converter.DTOConverter;
import com.liferay.portal.vulcan.dto.converter.DTOConverterRegistry;
import com.liferay.portal.vulcan.dto.converter.DefaultDTOConverterContext;
import com.liferay.portal.vulcan.pagination.Page;
import com.liferay.portal.vulcan.pagination.Pagination;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ServiceScope;

/**
 * @author Danny Situ
 */
@Component(
	properties = "OSGI-INF/liferay/rest/v1_0/signature-request.properties",
	scope = ServiceScope.PROTOTYPE, service = SignatureRequestResource.class
)
public class SignatureRequestResourceImpl
	extends BaseSignatureRequestResourceImpl {

	@Override
	public SignatureRequest getSignatureRequest(Long signatureRequestId)
		throws Exception {

		DSRequest dsRequest = _fetchDSRequest(signatureRequestId);

		_checkPermission(dsRequest, ActionKeys.VIEW);

		return _toSignatureRequest(dsRequest);
	}

	@Override
	public Page<SignatureRequest> getSignatureRequestsAssignedToMePage(
			String search, Pagination pagination)
		throws Exception {

		return Page.of(
			transform(
				_dsRequestManager.getRecipientDSRequests(
					contextCompany.getCompanyId(), contextUser.getUserId(),
					search, pagination.getStartPosition(),
					pagination.getEndPosition()),
				this::_toSignatureRequest),
			pagination,
			_dsRequestManager.getRecipientDSRequestsCount(
				contextCompany.getCompanyId(), contextUser.getUserId(),
				search));
	}

	@Override
	public Page<SignatureRequest> getSiteSignatureRequestsPage(
			Long siteId, String search, Pagination pagination)
		throws Exception {

		return Page.of(
			transform(
				_dsRequestManager.getSiteDSRequests(
					contextCompany.getCompanyId(), siteId, search,
					pagination.getStartPosition(), pagination.getEndPosition()),
				this::_toSignatureRequest),
			pagination,
			_dsRequestManager.getSiteDSRequestsCount(
				contextCompany.getCompanyId(), siteId, search));
	}

	@Override
	public SignatureRequest patchSignatureRequest(
			Long signatureRequestId, SignatureRequest signatureRequest)
		throws Exception {

		DSRequest dsRequest = _fetchDSRequest(signatureRequestId);

		_checkPermission(dsRequest, ActionKeys.UPDATE);

		String status = signatureRequest.getStatus();

		if (Validator.isNull(status) ||
			Objects.equals(status, dsRequest.getStatus())) {

			return _toSignatureRequest(dsRequest);
		}

		if (!Objects.equals(status, DSRequestConstants.STATUS_VOIDED)) {
			throw new BadRequestException(
				StringBundler.concat(
					"Unable to change the status of signature request ",
					signatureRequestId, " to \"", status, "\""));
		}

		if (dsRequest.isTerminal()) {
			throw new BadRequestException(
				StringBundler.concat(
					"Unable to void signature request ", signatureRequestId,
					" with status \"", dsRequest.getStatus(), "\""));
		}

		String voidReason = signatureRequest.getVoidReason();

		if (Validator.isNull(voidReason)) {
			throw new BadRequestException(
				"A reason is required to void a signature request");
		}

		_dsRequestManager.voidDSRequest(
			contextCompany.getCompanyId(), dsRequest.getSiteGroupId(),
			dsRequest, voidReason);

		return _toSignatureRequest(
			_dsRequestManager.fetchDSRequest(signatureRequestId));
	}

	@Override
	public SignatureRequest postSignatureRequestNotification(
			Long signatureRequestId)
		throws Exception {

		DSRequest dsRequest = _fetchDSRequest(signatureRequestId);

		_checkPermission(dsRequest, ActionKeys.UPDATE);

		_dsRequestManager.sendDSRequestNotifications(
			contextCompany.getCompanyId(), dsRequest.getSiteGroupId(),
			dsRequest);

		return _toSignatureRequest(dsRequest);
	}

	@Override
	public SignatureRequest postSiteSignatureRequest(
			Long siteId, SignatureRequest signatureRequest)
		throws Exception {

		Long[] fileEntryIds = signatureRequest.getFileEntryIds();

		if (ArrayUtil.isEmpty(fileEntryIds)) {
			throw new BadRequestException(
				"A signature request must have at least one file entry");
		}

		_groupService.getGroup(siteId);

		for (Long fileEntryId : fileEntryIds) {
			_dlAppService.getFileEntry(fileEntryId);
		}

		DSRequest dsRequest = _dsRequestManager.addDSRequest(
			contextCompany.getCompanyId(), siteId, contextUser.getUserId(),
			_toDSEnvelope(signatureRequest), ArrayUtil.toArray(fileEntryIds));

		if (GetterUtil.getBoolean(
				signatureRequest.getSendNotifications(), true)) {

			_dsRequestManager.sendDSRequestNotifications(
				contextCompany.getCompanyId(), siteId, dsRequest);
		}

		return _toSignatureRequest(dsRequest);
	}

	private void _checkPermission(DSRequest dsRequest, String actionId)
		throws Exception {

		if (!_dsRequestManager.hasPermission(
				PermissionThreadLocal.getPermissionChecker(), dsRequest,
				actionId)) {

			throw new PrincipalException.MustHavePermission(
				contextUser.getUserId(), DSRequest.class.getName(),
				dsRequest.getDSRequestId(), actionId);
		}
	}

	private DSRequest _fetchDSRequest(Long signatureRequestId) {
		DSRequest dsRequest = _dsRequestManager.fetchDSRequest(
			signatureRequestId);

		if ((dsRequest == null) ||
			(dsRequest.getCompanyId() != contextCompany.getCompanyId())) {

			throw new NotFoundException(
				"Unable to find signature request " + signatureRequestId);
		}

		return dsRequest;
	}

	private Map<String, Map<String, String>> _getActions(DSRequest dsRequest)
		throws Exception {

		if (!dsRequest.isSignatureRequired(contextUser.getEmailAddress())) {
			return Collections.emptyMap();
		}

		return HashMapBuilder.<String, Map<String, String>>put(
			"sign",
			HashMapBuilder.put(
				"href",
				_signDSURLProvider.getURL(
					contextCompany.getCompanyId(), dsRequest.getSiteGroupId(),
					dsRequest.getDSRequestId())
			).put(
				"method", "GET"
			).build()
		).build();
	}

	private DSEnvelope _toDSEnvelope(SignatureRequest signatureRequest)
		throws Exception {

		DSEnvelope dsEnvelope = new DSEnvelope();

		dsEnvelope.setDSRecipients(
			_toDSRecipients(signatureRequest.getSignatureRequestRecipients()));
		dsEnvelope.setEmailBlurb(signatureRequest.getEmailBody());
		dsEnvelope.setEmailSubject(signatureRequest.getEmailSubject());
		dsEnvelope.setExpireAfter(
			GetterUtil.getInteger(signatureRequest.getExpireAfter()));
		dsEnvelope.setExpireWarn(
			GetterUtil.getInteger(signatureRequest.getExpireWarn()));
		dsEnvelope.setName(signatureRequest.getName());

		return dsEnvelope;
	}

	private List<DSRecipient> _toDSRecipients(
			SignatureRequestRecipient[] signatureRequestRecipients)
		throws Exception {

		if (ArrayUtil.isEmpty(signatureRequestRecipients)) {
			throw new BadRequestException(
				"A signature request must have at least one recipient");
		}

		List<DSRecipient> dsRecipients = new ArrayList<>();

		for (SignatureRequestRecipient signatureRequestRecipient :
				signatureRequestRecipients) {

			Long userId = signatureRequestRecipient.getUserId();

			if (userId == null) {
				throw new BadRequestException(
					"A signature request recipient must have a user");
			}

			User user = _userLocalService.fetchUser(userId);

			if ((user == null) ||
				(user.getCompanyId() != contextCompany.getCompanyId())) {

				throw new NoSuchUserException("Unable to find user " + userId);
			}

			DSRecipient dsRecipient = new DSRecipient();

			dsRecipient.setDSRecipientId(
				String.valueOf(dsRecipients.size() + 1));
			dsRecipient.setEmailAddress(user.getEmailAddress());
			dsRecipient.setName(user.getFullName());

			dsRecipients.add(dsRecipient);
		}

		return dsRecipients;
	}

	private SignatureRequest _toSignatureRequest(DSRequest dsRequest)
		throws Exception {

		if (dsRequest == null) {
			return null;
		}

		return _signatureRequestDTOConverter.toDTO(
			new DefaultDTOConverterContext(
				contextAcceptLanguage.isAcceptAllLanguages(),
				_getActions(dsRequest), _dtoConverterRegistry,
				dsRequest.getDSRequestId(),
				contextAcceptLanguage.getPreferredLocale(), contextUriInfo,
				contextUser),
			dsRequest);
	}

	@Reference
	private DLAppService _dlAppService;

	@Reference
	private DSRequestManager _dsRequestManager;

	@Reference
	private DTOConverterRegistry _dtoConverterRegistry;

	@Reference
	private GroupService _groupService;

	@Reference
	private SignDSURLProvider _signDSURLProvider;

	@Reference(
		target = "(component.name=com.liferay.digital.signature.rest.internal.dto.v1_0.converter.SignatureRequestDTOConverter)"
	)
	private DTOConverter<DSRequest, SignatureRequest>
		_signatureRequestDTOConverter;

	@Reference
	private UserLocalService _userLocalService;

}