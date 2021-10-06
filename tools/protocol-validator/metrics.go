package main

import (
	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promauto"
)

var (
	namespace    = "signald"
	registry     = prometheus.NewRegistry()
	fieldsByType = promauto.NewCounterVec(prometheus.CounterOpts{
		Namespace: namespace,
		Name:      "fields_by_type",
	}, []string{"version", "type"})
	warningsMetric = promauto.NewCounterVec(prometheus.CounterOpts{
		Namespace: namespace,
		Name:      "protocol_validation_warnings",
	}, []string{"type"})
	failureMetric = promauto.NewCounterVec(prometheus.CounterOpts{
		Namespace: namespace,
		Name:      "protocol_validation_failure",
	}, []string{"type"})
)

func init() {
	registry.MustRegister(fieldsByType, warningsMetric, failureMetric)
}

func recordMetrics(outputs checkOutputs) {
	for version, types := range protocol.Types {
		for t, ty := range types {
			fieldsByType.WithLabelValues(version, t).Add(float64(len(ty.Fields)))
		}
	}
	for _, w := range outputs.warnings {
		warningsMetric.WithLabelValues(w.id).Inc()
	}
	for _, f := range outputs.failures {
		failureMetric.WithLabelValues(f.id).Inc()
	}
	prometheus.WriteToTextfile("metrics.txt", registry)
}
