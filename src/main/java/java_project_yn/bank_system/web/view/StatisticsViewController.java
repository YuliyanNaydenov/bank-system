package java_project_yn.bank_system.web.view;

import java_project_yn.bank_system.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/statistics")
@RequiredArgsConstructor
public class StatisticsViewController {

    private final StatisticsService statisticsService;

    @GetMapping
    public String overview(Model model) {
        model.addAttribute("stats", statisticsService.getOverview());
        return "statistics/statistics";
    }
}
