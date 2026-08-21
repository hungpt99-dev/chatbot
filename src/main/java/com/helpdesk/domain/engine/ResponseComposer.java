package com.helpdesk.domain.engine;

import com.helpdesk.web.dto.SopResponse;
import org.springframework.stereotype.Component;

/**
 * Builds the employee-facing assistant message from a deterministic engine result.
 * Keeps the assistant on-script: it paraphrases the current step's instruction
 * and, when the step exposes branches, asks the user to pick one — it never adds
 * free-form troubleshooting the SOP does not contain.
 */
@Component
public class ResponseComposer {

    public String compose(EngineResult result, SopResponse sop) {
        SopResponse.StepDto step = result.currentStep();
        if (step == null) {
            return "Xin lỗi, tôi không tìm thấy bước xử lý tiếp theo trong quy trình này. "
                    + "Tôi sẽ chuyển yêu cầu lên bộ phận IT hỗ trợ.";
        }
        if (result.conversationOver()) {
            if ("RESOLVED".equals(result.status())) {
                return "Tuyệt vời! Theo quy trình, vấn đề đã được xử lý xong. "
                        + "Nếu bạn gặp lại sự cố, hãy nhắn lại cho tôi nhé.";
            }
            return "Tôi đã thực hiện các bước xử lý theo quy trình nhưng chưa khắc phục được. "
                    + "Tôi sẽ chuyển yêu cầu này lên bộ phận IT hỗ trợ để được giúp đỡ tiếp.";
        }
        return sop.responseBody(step);
    }
}
