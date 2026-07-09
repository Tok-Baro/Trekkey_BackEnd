package com.api.trekkey.domain.organization.web.controller;

import com.api.trekkey.domain.organization.service.OrganizationService;
import com.api.trekkey.domain.organization.web.dto.OrganizationSearchRes;
import com.api.trekkey.global.response.SuccessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/organizations")
public class OrganizationController {
    private final OrganizationService organizationService;

    @GetMapping
    public ResponseEntity<SuccessResponse<List<OrganizationSearchRes>>> searchActiveOrganizations(
            @RequestParam(required = false) String keyword
    ){
        List<OrganizationSearchRes> schoolList = organizationService.searchActiveOrganizations(keyword);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(SuccessResponse.ok(schoolList));
    }
}
