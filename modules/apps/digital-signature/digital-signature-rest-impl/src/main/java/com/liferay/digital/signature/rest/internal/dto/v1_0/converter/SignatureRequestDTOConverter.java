/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.digital.signature.rest.internal.dto.v1_0.converter;

import com.liferay.digital.signature.model.DSRequest;
import com.liferay.digital.signature.model.DSRequestRecipient;
import com.liferay.digital.signature.rest.dto.v1_0.SignatureRequest;
import com.liferay.digital.signature.rest.dto.v1_0.SignatureRequestRecipient;
import com.liferay.document.library.kernel.service.DLAppLocalService;
import com.liferay.petra.function.transform.TransformUtil;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.vulcan.dto.converter.DTOConverter;
import com.liferay.portal.vulcan.dto.converter.DTOConverterContext;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Danny Situ
 */
@Component(
	property = "dto.class.name=com.liferay.digital.signature.model.DSRequest",
	service = DTOConverter.class
)
public class SignatureRequestDTOConverter
	implements DTOConverter<DSRequest, SignatureRequest> {

	@Override
	public String getContentType() {
		return SignatureRequest.class.getSimpleName();
	}

	@Override
	public SignatureRequest toDTO(
			DTOConverterContext dtoConverterContext, DSRequest dsRequest)
		throws Exception {

		if (dsRequest == null) {
			return null;
		}

		return new SignatureRequest() {
			{
				setActions(dtoConverterContext::getActions);
				setDateCreated(dsRequest::getCreateDate);
				setDocumentTitles(() -> _getDocumentTitles(dsRequest));
				setEmailBody(dsRequest::getEmailBody);
				setEmailSubject(dsRequest::getEmailSubject);
				setExpirationDate(dsRequest::getExpirationDate);
				setId(dsRequest::getDSRequestId);
				setProviderKey(dsRequest::getProviderKey);
				setProviderRequestId(dsRequest::getProviderRequestId);
				setRequesterEmailAddress(dsRequest::getRequesterEmailAddress);
				setRequesterName(dsRequest::getRequesterName);
				setRequesterUserId(dsRequest::getRequesterUserId);
				setSignatureRequestRecipients(
					() -> TransformUtil.transformToArray(
						dsRequest.getDSRequestRecipients(),
						dsRequestRecipient -> _toSignatureRequestRecipient(
							dsRequestRecipient),
						SignatureRequestRecipient.class));
				setStatus(dsRequest::getStatus);
				setStatusDate(dsRequest::getStatusDate);
			}
		};
	}

	private String _getDocumentTitles(DSRequest dsRequest) {
		return StringUtil.merge(
			TransformUtil.transform(
				dsRequest.getFileEntryIds(),
				fileEntryId -> {
					FileEntry fileEntry = _dlAppLocalService.fetchFileEntry(
						fileEntryId);

					if (fileEntry == null) {
						return null;
					}

					return fileEntry.getTitle();
				}),
			", ");
	}

	private SignatureRequestRecipient _toSignatureRequestRecipient(
		DSRequestRecipient dsRequestRecipient) {

		return new SignatureRequestRecipient() {
			{
				setEmailAddress(dsRequestRecipient::getEmailAddress);
				setName(dsRequestRecipient::getName);
				setProviderRecipientId(
					dsRequestRecipient::getProviderRecipientId);
				setSentDate(dsRequestRecipient::getSentDate);
				setStatus(dsRequestRecipient::getStatus);
				setStatusDate(dsRequestRecipient::getStatusDate);
				setUserId(dsRequestRecipient::getUserId);
			}
		};
	}

	@Reference
	private DLAppLocalService _dlAppLocalService;

}