package main

import (
	"fmt"
)

func checkMissingCriticalFields() (response checkOutput) {
	if protocol.DocVersion == "" {
		response.failures = append(response.failures, "[MissingCriticalFields] root doc_version field is empty")
	}

	if len(protocol.Actions) == 0 {
		response.failures = append(response.failures, "[MissingCriticalFields] actions list is empty")
	} else {
		for version, actions := range protocol.Actions {
			if len(actions) == 0 {
				response.failures = append(response.failures, fmt.Sprintf("[MissingCriticalFields] .actions.%s is empty", version))
			}
		}
	}

	if len(protocol.Types) == 0 {
		response.failures = append(response.failures, "[MissingCriticalFields] actions list is empty")
	} else {
		for version, types := range protocol.Types {
			if len(types) == 0 {
				response.failures = append(response.failures, fmt.Sprintf("[MissingCriticalFields] .types.%s is empty", version))
			}
		}
	}
	return
}
