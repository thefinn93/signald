package main

import (
	"fmt"
	"unicode"
)

var (
	// override a specific field to downgrade it to a warning.
	overrides = map[string]map[string]map[string]bool{
		"v1": {
			"SendSuccess": {
				"needsSync": true, // v1 SendSuccess was added in !77 to replace a libsignal type that inadvertently made it into the protocol.
			},
		},
	}
)

func checkTypeFieldCasing(version, t, field string, _ DataType) (response checkOutputs) {
	if !unicode.IsUpper(rune(t[0])) {
		m := fmt.Sprintf("[TypeNameStartsWithLowerCase] %s.%s does not start with a capital letter", version, t)
		response.failures = append(response.failures, checkOutput{id: "FieldCasing", String: m})
	}
	for _, r := range field {
		if unicode.IsUpper(r) {
			m := fmt.Sprintf("[UpperCaseInFieldName] %s.%s has a field name with an upper case letter in it: %s", version, t, field)
			if version == "v0" {
				response.warnings = append(response.warnings, checkOutput{id: "FieldCasing", String: m})
			} else if overrides[version][t][field] {
				response.warnings = append(response.warnings, checkOutput{id: "FieldCasing", String: m})
			} else {
				response.failures = append(response.failures, checkOutput{id: "FieldCasing", String: m})
			}
			break
		}
	}
	return
}
