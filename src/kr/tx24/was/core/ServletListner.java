package kr.tx24.was.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import kr.tx24.lib.lang.CommonUtils;
import kr.tx24.lib.lang.SystemUtils;
import kr.tx24.was.main.Server;

/**
 * @author juseop
 *
 */
@WebListener
public class ServletListner implements ServletContextListener{

	private static Logger logger 	= LoggerFactory.getLogger(ServletListner.class );
	
	@Override
	public void contextDestroyed(ServletContextEvent event) {
        if(SystemUtils.deepview()) {
            logger.info("ServletContextListener destroyed");
        }
	}

	//Servlet 이 시작될 때 또는 reload 되어 시작될때.
	@Override
	public void contextInitialized(ServletContextEvent event) {
		if(SystemUtils.deepview()) {
			logger.info("ServletContextListener initialized");
		}
		
		ServletContext ctx = event.getServletContext();
		ctx.setSessionTimeout(0); //JSESSION DISABLE
		
		if(!CommonUtils.isEmpty(Server.config.parameter)) {
			for(String key: Server.config.parameter.keySet()) {
				System.setProperty(key, Server.config.parameter.get(key));
			}
		}

	}
}
