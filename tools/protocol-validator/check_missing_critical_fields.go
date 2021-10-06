package main

import (
	"fmt"
)

func checkMissingCriticalFields() (response checkOutputs) {
	if protocol.DocVersion == "" {
		response.failures = append(response.failures, checkOutput{id: "RootDocVersionFieldEmpty", String: "root doc_version field is empty"})
	}

	if len(protocol.Actions) == 0 {
		response.failures = append(response.failures, checkOutput{id: "MissingCriticalFields", String: "actions list is empty"})
	} else {
		for version, actions := range protocol.Actions {
			if len(actions) == 0 {
				response.failures = append(response.failures, checkOutput{id: "MissingCriticalFields", String: fmt.Sprintf(".actions.%s is empty", version)})
			}
		}
	}

	if len(protocol.Types) == 0 {
		response.failures = append(response.failures, checkOutput{id: "MissingCriticalFields", String: "actions list is empty"})
	} else {
		for version, types := range protocol.Types {
			if len(types) == 0 {
				response.failures = append(response.failures, checkOutput{id: "MissingCriticalFields", String: fmt.Sprintf(".types.%s is empty", version)})
			}
		}
	}
	return
}
