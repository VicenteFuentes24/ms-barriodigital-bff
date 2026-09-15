package cl.barriodigital.barriodigitalbff.controller;

import cl.barriodigital.barriodigitalbff.config.BarriodigitalProperties;
import cl.barriodigital.barriodigitalbff.service.DownstreamProxyService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/report")
public class ReportProxyController {

    private final DownstreamProxyService proxyService;
    private final BarriodigitalProperties properties;

    public ReportProxyController(DownstreamProxyService proxyService, BarriodigitalProperties properties) {
        this.proxyService = proxyService;
        this.properties = properties;
    }

    @RequestMapping(value = {"", "/**"}, method = RequestMethod.GET)
    public ResponseEntity<byte[]> proxy(HttpServletRequest request) {
        return proxyService.forward(
                properties.getServices().getReportUrl(),
                "reportería",
                request,
                null);
    }
}
