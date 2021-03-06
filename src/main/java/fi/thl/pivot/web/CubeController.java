package fi.thl.pivot.web;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import fi.thl.pivot.annotation.Monitored;
import fi.thl.pivot.exception.CubeAccessDeniedException;
import fi.thl.pivot.exception.CubeNotFoundException;

/**
 * Cube controller for the UI part of the pivot controller
 * 
 * @author aleksiyrttiaho
 *
 */
@Controller
@RequestMapping("/{env}/{locale}/{subject}/{hydra}/fact_{cube}")
public class CubeController extends AbstractCubeController {

    private static final Logger LOG = Logger.getLogger(CubeController.class);

    @Monitored
    @RequestMapping(value = "", produces = "text/html;charset=UTF-8", method = RequestMethod.GET)
    public String displayCube(@ModelAttribute CubeRequest cubeRequest, HttpServletRequest request, Model model)
            throws CubeNotFoundException, CubeAccessDeniedException {
        LOG.info(String.format("ACCESS HTML cube requested %s %s %s", cubeRequest.getEnv(), cubeRequest.getCube(), cubeRequest.toString()));

        CubeService cs = createCube(cubeRequest, resolveSearchType(cubeRequest.getSearchType()), model);
        if (cs.isCubeCreated()) {

            if (cs.isCubeAccessDenied()) {
                throw new CubeAccessDeniedException(cubeRequest.getEnv(), cubeRequest.getCube());
            }

            logSource.logDisplayEvent(cubeRequest.getCube(), cubeRequest.getEnv(), cs, "html");

            model.addAttribute("env", cubeRequest.getEnv());
            model.addAttribute("cube", cubeRequest.getCube());
            model.addAttribute("rowParams", cubeRequest.getRowHeaders());
            model.addAttribute("colParams", cubeRequest.getColumnHeaders());

            model.addAttribute("isOpenData", cs.getSource().isOpenData());
            model.addAttribute("runDate", cs.getSource().getRunDate());

            model.addAttribute("dimensionsUrl", cubeRequest.getDimensionsUrl());

            model.addAttribute("uiLanguage", cubeRequest.getLocale());
            model.addAttribute("lang", cubeRequest.getLocale().getLanguage());
            model.addAttribute("target", cubeRequest.getTarget());
            
            model.addAttribute("metaLink", cs.getSource().getPredicates().get("meta:link"));

            return "cube";
        } else {
            model.addAttribute("cube", cubeRequest.getCube());
            throw new CubeNotFoundException();
        }
    }

    @RequestMapping(value = "", produces = "text/html;charset=UTF-8", method = RequestMethod.POST)
    public String loginToCube(@ModelAttribute CubeRequest cubeRequest, @RequestParam String password, HttpServletResponse response) {
        login(cubeRequest.getEnv(), cubeRequest.getCube(), password);
        return "redirect:/" + cubeRequest.getCubeUrl();
    }

    @RequestMapping(value = "/logout", produces = "text/html;charset=UTF-8")
    public String logout(@ModelAttribute CubeRequest cubeRequest, @PathVariable String env, @PathVariable String cube) {
        logout();
        return "redirect:/" + cubeRequest.getCubeUrl();
    }

}
