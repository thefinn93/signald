package main

import (
	"fmt"
	"unicode"
)

func checkTypeFieldCasing(version, t, field string, _ DataType) (response checkOutput) {
	if !unicode.IsUpper(rune(t[0])) {
		m := fmt.Sprintf("[TypeNameStartsWithLowerCase] %s.%s does not start with a capital letter", version, t)
		response.failures = append(response.failures, m)

	}
	for _, r := range field {
		if unicode.IsUpper(r) {
			m := fmt.Sprintf("[UpperCaseInFieldName] %s.%s has a field name with an upper case letter in it: %s", version, t, field)
			response.failures = append(response.failures, m)
			break
		}
	}
	return
}
