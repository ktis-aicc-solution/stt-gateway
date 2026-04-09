package com.ktis.stt_gateway.api;

import com.ktis.stt_gateway.domain.Call;
import com.ktis.stt_gateway.domain.CallStatus;
import com.ktis.stt_gateway.dto.CallDto;
import com.ktis.stt_gateway.dto.PageResponseDto;
import com.ktis.stt_gateway.exception.CallNotFoundException;
import com.ktis.stt_gateway.repository.CallRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/calls")
@RequiredArgsConstructor
@Slf4j
public class CallController {

    private final CallRepository callRepository;

    @GetMapping
    public ResponseEntity<List<CallDto>> getActiveCalls() {
        List<CallDto> activeCalls = callRepository
            .findByStatusInOrderByStartTimeDesc(List.of(CallStatus.RINGING, CallStatus.ACTIVE))
            .stream()
            .map(CallDto::fromEntity)
            .collect(Collectors.toList());
        return ResponseEntity.ok(activeCalls);
    }

    @GetMapping("/{callId}")
    public ResponseEntity<CallDto> getCall(@PathVariable String callId) {
        Call call = callRepository.findByCallId(callId)
            .orElseThrow(() -> new CallNotFoundException(callId));
        return ResponseEntity.ok(CallDto.fromEntity(call));
    }

    @GetMapping("/history")
    public ResponseEntity<PageResponseDto<CallDto>> getCallHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        if (from == null) from = LocalDateTime.now().minusDays(7);
        if (to == null) to = LocalDateTime.now();

        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startTime"));
        var pageResult = callRepository.findByDateRange(from, to, pageable)
            .map(CallDto::fromEntity);

        return ResponseEntity.ok(PageResponseDto.from(pageResult));
    }
}
