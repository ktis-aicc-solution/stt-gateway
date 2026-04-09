package com.ktis.stt_gateway.web;

import com.ktis.stt_gateway.capture.PacketCaptureService;
import com.ktis.stt_gateway.domain.CallStatus;
import com.ktis.stt_gateway.repository.CallRepository;
import com.ktis.stt_gateway.session.CallSessionManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/")
@RequiredArgsConstructor
public class DashboardController {

    private final CallRepository callRepository;
    private final PacketCaptureService captureService;
    private final CallSessionManager sessionManager;

    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("activeCalls",
            callRepository.findByStatusInOrderByStartTimeDesc(List.of(CallStatus.RINGING, CallStatus.ACTIVE)));
        model.addAttribute("captureRunning", captureService.isRunning());
        model.addAttribute("activeSessionCount", sessionManager.getActiveSessionCount());
        return "dashboard";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
