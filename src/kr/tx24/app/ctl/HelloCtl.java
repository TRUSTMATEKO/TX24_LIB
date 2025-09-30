package kr.tx24.app.ctl;

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
public class HelloCtl {

	@SessionIgnore
    @GetMapping("/hello")
    public String hello(Model model, @Header LinkedMap<String,Object>  headerMap, @Param SharedMap<String,Object> paramMap) {
        model.addAttribute("name", "Thymeleaf 테스트11");
        
 
        System.out.println(headerMap.toJson());
        System.out.println(paramMap.toJson());
        
        
        return "hello"; // templates/hello.html
    }
}