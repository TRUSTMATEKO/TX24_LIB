package kr.tx24.inter.mapper;
 
import java.lang.reflect.Method;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.ClassPath;

import kr.tx24.lib.lang.Abbreviator;
import kr.tx24.lib.lang.CommonUtils;
import kr.tx24.lib.lang.SystemUtils;
import kr.tx24.lib.map.LinkedMap;

public class Router {
	
	private static final Logger logger = LoggerFactory.getLogger(Router.class);
	
	public static LinkedMap<String, RouteMap> ROUTE_MAP	 = null;
	
	public static void start(String packageName) {
		try {
			logger.info("scan package : {}",packageName);
			if(ROUTE_MAP == null && !CommonUtils.isEmpty(packageName)) {
				ROUTE_MAP 		= new LinkedMap<String, RouteMap>();
				scanPackage(packageName);

				if(SystemUtils.deepview()) {
					StringBuilder sb = new StringBuilder();
					for(String key : ROUTE_MAP.keySet()) {
						sb
						.append(CommonUtils.paddingSpace(Abbreviator.format(key,'/', 19),20))
						.append(" = ")
						.append(ROUTE_MAP.get(key).toString());
					}
					logger.info("route map\n{}",sb.toString());
				}
			}
			
		}catch(Exception e) {
			logger.info(CommonUtils.getExceptionMessage(e));
			e.printStackTrace();
			System.exit(1);
		}
	}
	
	private static void scanPackage(String packageName) throws Exception{ 
		ClassPath classPath = ClassPath.from(getDefaultClassLoader());
		ImmutableSet<ClassPath.ClassInfo> classes = classPath.getTopLevelClassesRecursive(packageName);
		for (ClassPath.ClassInfo clp : classes) {
			Class<?> clazz = Class.forName(clp.getName());	
			if(clazz.isAnnotationPresent(Controller.class)) {
				registerController(clazz);	
			}
			
		}
		if (ROUTE_MAP.size() == 0) {
			System.out.println("route annotation not found!");
		}
	}
	
	
	
	private static void registerController(Class<?> clazz){
		String rootTarget = ends(clazz.getAnnotation(Controller.class).target());

		for (Method method : clazz.getDeclaredMethods()) {
			if(method.isAnnotationPresent(Route.class)){
				Route route = method.getAnnotation(Route.class);
				
				RouteMap map = new RouteMap();
				map.cls  = clazz;
				map.loggable = route.loggable();
				map.method = method;
				for(String target : route.target()){
					String t = rootTarget+ends(target);
					if(ROUTE_MAP.containsKey(t)){
						System.out.printf("[duplicated target] %s overwrited!\n",t);
					}
					ROUTE_MAP.put(t.toLowerCase(),map);
				}
			}
		}
		
	}

	private static ClassLoader getDefaultClassLoader(){
		ClassLoader cl = null;
		try{
			cl = Thread.currentThread().getContextClassLoader();
		}catch(Throwable ex){
		}
		if(cl == null){
			try{
				cl = ClassLoader.getSystemClassLoader();
			}catch(Throwable ex){}
		}

		return cl;
	}
	
	
	private static String ends(String s){
		if(s.endsWith("/")){
			return s.substring(0,s.length()-1);
		}
		return s.toLowerCase();
	}
	
	
	
	public static RouteMap getRoute(String target){
		long count = target.chars().filter(ch -> ch == '/').count();
		RouteMap routeMap = null;
		
		for(int i=0;i<count;i++){
			routeMap = ROUTE_MAP.get(target.toLowerCase());
			if(routeMap != null){
				break;
			}
		}
		return routeMap;
	}

	

	

	
	
	


	
	
	

}

