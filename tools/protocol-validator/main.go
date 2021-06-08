package main

import (
	"encoding/json"
	"fmt"
	"os"

	aurora "github.com/logrusorgru/aurora/v3"
)

var protocol Protocol

type checkOutput struct {
	warnings []string
	failures []string
}

type check func() checkOutput

var checks = []check{checkRequestResponseTypesExist, checkMissingCriticalFields}

func main() {
	err := json.NewDecoder(os.Stdin).Decode(&protocol)
	if err != nil {
		fmt.Println(aurora.Red("error parsing stdin"))
		panic(err)
	}
	combinedOutput := checkOutput{}
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

	if len(combinedOutput.failures) > 0 {
		os.Exit(1)
	}
}
