package kr.tx24.was.core;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import kr.tx24.was.conf.TomcatConfig;
import kr.tx24.was.conf.TomcatConfigLoader;

@Component
public class ScanConfig implements BeanDefinitionRegistryPostProcessor {
	private static Logger logger = LoggerFactory.getLogger( ScanConfig.class );
	
	
    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        
    	// scanning 코드 실행
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        ClassPathBeanDefinitionScanner scanner = new ClassPathBeanDefinitionScanner(registry, true);
        scanner.setResourceLoader(new PathMatchingResourcePatternResolver(original));
        

        TomcatConfig config = TomcatConfigLoader.load();
        
        String[] packages = config.basePackage.split(",");
        if (packages != null && Arrays.stream(packages).map(String::strip).anyMatch(s -> !s.isEmpty())) {
        	logger.info("scanning packages : {}", config.basePackage);
        	scanner.scan(packages);
        }else {
        	scanner.scan("kr.tx24");
        }
        
        
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
    }

}
