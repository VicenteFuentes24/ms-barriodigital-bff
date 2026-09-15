package cl.barriodigital.barriodigitalbff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "barriodigital")
public class BarriodigitalProperties {

    private Security security = new Security();
    private Frontend frontend = new Frontend();
    private Services services = new Services();

    public Security getSecurity() {
        return security;
    }

    public void setSecurity(Security security) {
        this.security = security;
    }

    public Frontend getFrontend() {
        return frontend;
    }

    public void setFrontend(Frontend frontend) {
        this.frontend = frontend;
    }

    public Services getServices() {
        return services;
    }

    public void setServices(Services services) {
        this.services = services;
    }

    public static class Security {
        private String audience;

        public String getAudience() {
            return audience;
        }

        public void setAudience(String audience) {
            this.audience = audience;
        }
    }

    public static class Frontend {
        private String origin = "http://localhost:5173";

        public String getOrigin() {
            return origin;
        }

        public void setOrigin(String origin) {
            this.origin = origin;
        }
    }

    public static class Services {
        private String requestsUrl = "http://localhost:8081";
        private String catalogUrl = "http://localhost:8082";
        private String reportUrl = "http://localhost:8083";
        private String auditUrl = "http://localhost:8084";

        public String getRequestsUrl() {
            return requestsUrl;
        }

        public void setRequestsUrl(String requestsUrl) {
            this.requestsUrl = requestsUrl;
        }

        public String getCatalogUrl() {
            return catalogUrl;
        }

        public void setCatalogUrl(String catalogUrl) {
            this.catalogUrl = catalogUrl;
        }

        public String getReportUrl() {
            return reportUrl;
        }

        public void setReportUrl(String reportUrl) {
            this.reportUrl = reportUrl;
        }

        public String getAuditUrl() {
            return auditUrl;
        }

        public void setAuditUrl(String auditUrl) {
            this.auditUrl = auditUrl;
        }
    }
}
