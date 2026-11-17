package daiso.common.mvc;

import java.lang.reflect.InvocationTargetException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public interface HandlerAdapter {
	boolean supports(HandlerMethod handler);
	String handle(
	        HttpServletRequest request,
	        HttpServletResponse response,
	        HandlerMethod handler)
	        throws IllegalAccessException, InvocationTargetException;

}