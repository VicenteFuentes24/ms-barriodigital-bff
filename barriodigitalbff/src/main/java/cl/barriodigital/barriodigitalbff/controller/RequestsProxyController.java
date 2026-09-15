package cl.barriodigital.barriodigitalbff.controller;

import cl.barriodigital.barriodigitalbff.config.BarriodigitalProperties;
import cl.barriodigital.barriodigitalbff.service.DownstreamProxyService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/requests")
public class RequestsProxyController {

    private final DownstreamProxyService proxyService;
    private final BarriodigitalProperties properties;

    public RequestsProxyController(DownstreamProxyService proxyService, BarriodigitalProperties properties) {
        this.proxyService = proxyService;
        this.properties = properties;
    }

    @RequestMapping(value = {"", "/**"}, method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<byte[]> proxy(HttpServletRequest request, @RequestBody(required = false) byte[] body) {
        return proxyService.forward(
                properties.getServices().getRequestsUrl(),
                "trámites",
                request,
                body);
    }
}
