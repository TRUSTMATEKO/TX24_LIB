package kr.tx24.test.was.ctl;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import kr.tx24.lib.map.LinkedMap;
import kr.tx24.lib.map.SharedMap;
import kr.tx24.lib.mapper.JacksonUtils;
import kr.tx24.was.annotation.Header;
import kr.tx24.was.annotation.Param;
import kr.tx24.was.annotation.SessionIgnore;

@Controller
public class HelloCtl2 {
 
	@SessionIgnore
    @GetMapping("/hello2")
    public String hello(Model model, @Header LinkedMap<String,Object>  headerMap, @Param SharedMap<String,Object> paramMap) {
        model.addAttribute("name", "Thymeleaf Hello3");
        
 
        System.out.println(headerMap.toJson());
        System.out.println(paramMap.toJson());
         //testsettt
         
        return "hello2"; // templates/hello.html
    }
	
	@SessionIgnore
    @GetMapping("/hello3")
    public String hellㅐ3(Model model, @Header LinkedMap<String,Object>  headerMap, @Param SharedMap<String,Object> paramMap) {
        model.addAttribute("name", "Thymeleaf Helloxxxx");
        
 
        System.out.println(headerMap.toJson());
        System.out.println(paramMap.toJson());
         //testsettt
         
        return "hello3"; // templates/hello.html
    }
}