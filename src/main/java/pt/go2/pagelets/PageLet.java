package pt.go2.pagelets;

import java.io.IOException;

import pt.go2.application.HttpResponse;

import com.sun.net.httpserver.HttpExchange;

/**
 * @author vilaca
 * 
 */
public interface PageLet {

	HttpResponse getPageLet(final HttpExchange exchange)
			throws IOException;

}
