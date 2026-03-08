package com.uk.certifynow.certify_now.rest.dto.inspection;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record EpcRecordRequest(
    @NotNull @Valid EpcPropertyDetailsRequest propertyDetails,
    @NotNull @Valid EpcClientDetailsRequest clientDetails,
    @Valid EpcOccupierDetailsRequest occupierDetails,
    @NotNull @Valid EpcBookingDetailsRequest bookingDetails,
    @Valid EpcPreAssessmentRequest preAssessmentData,
    @Valid EpcPhotosRequest photos,
    @Valid EpcDocumentsRequest documents) {}
