package net.cnri.cordra.web;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

public class RemoteAddressRestrictionFilter implements Filter {

    private static Set<String> addresses;

    @Override
    public void init(FilterConfig config) throws ServletException {
        String addressesString = config.getInitParameter("addresses");
        addresses = Arrays.stream(addressesString.split(","))
            .map(String::trim)
            .map(this::throwingGetAllByName)
            .flatMap(Arrays::stream)
            .map(InetAddress::getHostAddress)
            .collect(Collectors.toSet());
    }

    private InetAddress[] throwingGetAllByName(String s) {
        try {
            return InetAddress.getAllByName(s);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void destroy() {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        HttpServletResponse response = (HttpServletResponse) res;
        if (isAuthorized(request)) {
            chain.doFilter(request, response);
        } else {
            request.getServletContext().log("Unauthorized request");
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        }
    }

    private boolean isAuthorized(ServletRequest request) {
        return addresses.contains(request.getRemoteAddr());
    }
}
