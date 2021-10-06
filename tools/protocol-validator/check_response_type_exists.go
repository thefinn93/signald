package main

import (
	"fmt"
)

var builtins = []string{"String"}

// validates that all response types exist in the specified version
func checkRequestResponseTypesExist() (response checkOutputs) {
	for version, actions := range protocol.Actions {
		for t, action := range actions {
			if !isBuiltin(action.Request) {
				if _, ok := protocol.Types[version][action.Request]; !ok {
					m := checkOutput{id: "MissingRequestType", String: fmt.Sprintf("request %s.%s has request type %s but no such type exists (is it referencing another version?)", t, version, action.Request)}
					response.failures = append(response.failures, m)
				}
			}

			if action.Response != "" && !isBuiltin(action.Response) {
				if _, ok := protocol.Types[version][action.Response]; !ok {
					m := checkOutput{id: "MissingRequestType", String: fmt.Sprintf("request %s.%s has response type %s but no such type exists (is it referencing another version?)", t, version, action.Response)}
					response.failures = append(response.failures, m)
				}
			}
		}
	}
	return
}

func isBuiltin(t string) bool {
	for _, b := range builtins {
		if t == b {
			return true
		}
	}
	return false
}
