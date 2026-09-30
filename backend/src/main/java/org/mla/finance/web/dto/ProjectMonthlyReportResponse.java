package org.mla.finance.web.dto;

import java.util.List;

public record ProjectMonthlyReportResponse(ProjectRef project, List<MonthlyAmount> months) {
}
