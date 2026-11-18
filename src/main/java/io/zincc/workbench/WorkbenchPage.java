package io.zincc.workbench;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class WorkbenchPage {

	@GetMapping("/workbench")
	String canonicalPath() {
		return "redirect:/workbench/";
	}

	@GetMapping("/workbench/")
	String index() {
		return "forward:/workbench/index.html";
	}

}
