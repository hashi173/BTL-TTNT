package com.coffeeshop.controller;

import com.coffeeshop.service.ai.NaiveBayesClassifier;
import com.coffeeshop.service.ai.RecommendationEvaluator;
import com.coffeeshop.service.ai.RecommendationEvaluator.ConfusionMatrixResult;
import com.coffeeshop.service.ai.RecommendationEvaluator.EvaluationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.*;

@Controller
@RequestMapping("/admin/ai")
@RequiredArgsConstructor
public class AdminAIController {

    private final RecommendationEvaluator evaluator;
    private final NaiveBayesClassifier naiveBayesClassifier;

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        // Main evaluation
        EvaluationResult mainResult = evaluator.evaluate();
        model.addAttribute("precision", r4(mainResult.getPrecision()));
        model.addAttribute("recall", r4(mainResult.getRecall()));
        model.addAttribute("f1", r4(mainResult.getF1Score()));
        model.addAttribute("hitRate", r4(mainResult.getHitRate()));
        model.addAttribute("map", r4(mainResult.getMap()));
        model.addAttribute("totalUsers", mainResult.getTotalUsers());
        model.addAttribute("evaluatedUsers", mainResult.getEvaluatedUsers());

        // Baseline comparison
        Map<String, EvaluationResult> baselines = evaluator.compareBaselines();
        model.addAttribute("baselines", baselines);

        // Ablation: weights
        Map<String, EvaluationResult> weightResults = evaluator.ablationStudyWeights();
        model.addAttribute("weightResults", weightResults);

        // Ablation: K
        Map<Integer, EvaluationResult> kResults = evaluator.ablationStudyK();
        model.addAttribute("kResults", kResults);

        // Naive Bayes confusion matrix
        ConfusionMatrixResult cmResult = evaluator.evaluateNaiveBayes(naiveBayesClassifier);
        model.addAttribute("confusionMatrix", cmResult);

        // Curves (JSON for Chart.js)
        model.addAttribute("prCurveJson", toJson(evaluator.precisionRecallCurve()));
        model.addAttribute("f1CurveJson", toJson(evaluator.f1ByKCurve()));

        return "admin/ai-dashboard";
    }

    // JSON endpoints for AJAX
    @GetMapping("/api/evaluate")
    @ResponseBody
    public Map<String, Object> apiEvaluate() {
        EvaluationResult r = evaluator.evaluate();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("precision", r4(r.getPrecision()));
        response.put("recall", r4(r.getRecall()));
        response.put("f1Score", r4(r.getF1Score()));
        response.put("hitRate", r4(r.getHitRate()));
        response.put("map", r4(r.getMap()));
        response.put("totalUsers", r.getTotalUsers());
        response.put("evaluatedUsers", r.getEvaluatedUsers());
        return response;
    }

    @GetMapping("/api/ablation-weights")
    @ResponseBody
    public Map<String, Map<String, Object>> apiAblationWeights() {
        Map<String, EvaluationResult> results = evaluator.ablationStudyWeights();
        Map<String, Map<String, Object>> response = new LinkedHashMap<>();
        for (var entry : results.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("precision", r4(entry.getValue().getPrecision()));
            m.put("recall", r4(entry.getValue().getRecall()));
            m.put("f1", r4(entry.getValue().getF1Score()));
            m.put("hitRate", r4(entry.getValue().getHitRate()));
            response.put(entry.getKey(), m);
        }
        return response;
    }

    @GetMapping("/api/ablation-k")
    @ResponseBody
    public Map<String, Map<String, Object>> apiAblationK() {
        Map<Integer, EvaluationResult> results = evaluator.ablationStudyK();
        Map<String, Map<String, Object>> response = new LinkedHashMap<>();
        for (var entry : results.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("precision", r4(entry.getValue().getPrecision()));
            m.put("recall", r4(entry.getValue().getRecall()));
            m.put("f1", r4(entry.getValue().getF1Score()));
            m.put("hitRate", r4(entry.getValue().getHitRate()));
            response.put(String.valueOf(entry.getKey()), m);
        }
        return response;
    }

    @GetMapping("/api/baselines")
    @ResponseBody
    public Map<String, Map<String, Object>> apiBaselines() {
        Map<String, EvaluationResult> results = evaluator.compareBaselines();
        Map<String, Map<String, Object>> response = new LinkedHashMap<>();
        for (var entry : results.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("precision", r4(entry.getValue().getPrecision()));
            m.put("recall", r4(entry.getValue().getRecall()));
            m.put("f1", r4(entry.getValue().getF1Score()));
            m.put("hitRate", r4(entry.getValue().getHitRate()));
            m.put("map", r4(entry.getValue().getMap()));
            response.put(entry.getKey(), m);
        }
        return response;
    }

    @GetMapping("/api/confusion-matrix")
    @ResponseBody
    public Map<String, Object> apiConfusionMatrix() {
        ConfusionMatrixResult cm = evaluator.evaluateNaiveBayes(naiveBayesClassifier);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("matrix", cm.getMatrix());
        response.put("accuracy", r4(cm.getAccuracy()));
        response.put("totalSamples", cm.getTotalSamples());
        response.put("precisionPerClass", cm.getPrecisionPerClass());
        response.put("recallPerClass", cm.getRecallPerClass());
        response.put("f1PerClass", cm.getF1PerClass());
        return response;
    }

    @GetMapping("/api/pr-curve")
    @ResponseBody
    public List<Map<String, Object>> apiPRCurve() {
        return evaluator.precisionRecallCurve();
    }

    @GetMapping("/api/f1-curve")
    @ResponseBody
    public List<Map<String, Object>> apiF1Curve() {
        return evaluator.f1ByKCurve();
    }

    @GetMapping("/api/keywords")
    @ResponseBody
    public Map<String, List<String>> apiKeywords() {
        return naiveBayesClassifier.getCategoryKeywords();
    }

    private double r4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    private String toJson(Object obj) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }
}
