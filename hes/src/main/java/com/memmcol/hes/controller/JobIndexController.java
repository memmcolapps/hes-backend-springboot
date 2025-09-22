package com.memmcol.hes.controller;

import java.util.List;

import com.memmcol.hes.model.SchedulerJobInfo;
import com.memmcol.hes.schedulers.SchedulerJobService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;


@Controller
@RequestMapping("/api/job")
public class JobIndexController {

    @Autowired
    private SchedulerJobService scheduleJobService;

    @GetMapping("/index")
    public String index(Model model){
        List<SchedulerJobInfo> jobList = scheduleJobService.getAllJobList();
        model.addAttribute("jobs", jobList);
        return "index";
    }

}
