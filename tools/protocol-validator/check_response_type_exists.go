package main

import (
	"fmt"
)

// validates that all response types exist in the specified version
func checkRequestResponseTypesExist() (response checkOutput) {
	for version, actions := range protocol.Actions {
		for t, action := range actions {
			if _, ok := protocol.Types[version][action.Request]; !ok {
				m := fmt.Sprintf("[MissingRequestType] request %s.%s has request type %s but no such type exists (is it referencing another version?)", t, version, action.Request)
				response.failures = append(response.failures, m)
			}
			if action.Response != "" {
				if _, ok := protocol.Types[version][action.Response]; !ok {
					m := fmt.Sprintf("[MissingResponseType] request %s.%s has response type %s but no such type exists (is it referencing another version?)", t, version, action.Response)
					response.failures = append(response.failures, m)
				}
			}
		}
	}
	return
}
