package main

import (
	"encoding/json"
	"fmt"
	"os"

	aurora "github.com/logrusorgru/aurora/v3"
)

var protocol Protocol

type checkOutput struct {
	id     string
	String string
}

type checkOutputs struct {
	warnings []checkOutput
	failures []checkOutput
}

type check func() checkOutputs

type fieldCheck func(string, string, string, DataType) checkOutputs

var checks = []check{checkRequestResponseTypesExist, checkMissingCriticalFields, checkErrorsExist}

var fieldChecks = []fieldCheck{checkTypeFieldCasing, checkCrossVersionReferences}

func main() {
	err := json.NewDecoder(os.Stdin).Decode(&protocol)
	if err != nil {
		fmt.Println(aurora.Red("error parsing stdin"))
		panic(err)
	}
	combinedOutput := checkOutputs{}
	for _, c := range checks {
		result := c()
		combinedOutput.failures = append(combinedOutput.failures, result.failures...)
		combinedOutput.warnings = append(combinedOutput.warnings, result.warnings...)
	}

	d, err := checkDiff()
	if err != nil {
		fmt.Println(aurora.Red("error diffing against stable protocol version"))
		panic(err)
	}
	combinedOutput.failures = append(combinedOutput.failures, d.failures...)
	combinedOutput.warnings = append(combinedOutput.warnings, d.warnings...)

	for _, failure := range combinedOutput.failures {
		fmt.Println(aurora.Red(aurora.Bold(failure)))
	}

	for _, warn := range combinedOutput.warnings {
		fmt.Println(aurora.Yellow(warn))
	}

	recordMetrics(combinedOutput)

	if len(combinedOutput.failures) > 0 {
		os.Exit(1)
	}
}
