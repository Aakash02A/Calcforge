package com.calcforge.dto.request;

import jakarta.validation.constraints.Size;

public record HistoryUpdateRequest(
        @Size(max = 500) String tags,
        Boolean favorite
) {
}
