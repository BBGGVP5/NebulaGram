package onboarding

import "testing"

func TestFlowOffersTunnelBeforePhone(t *testing.T) {
	var tunnelAt, phoneAt = -1, -1
	for i, step := range Flow() {
		switch step.Kind {
		case StepTunnel:
			tunnelAt = i
		case StepPhone:
			phoneAt = i
		}
	}
	if tunnelAt < 0 || phoneAt < 0 {
		t.Fatal("the flow must contain both the tunnel and the phone step")
	}
	if tunnelAt > phoneAt {
		t.Error("the tunnel step must come first: on a censored network the login code never arrives without it")
	}
}

func TestEveryStepHasAPrimaryAction(t *testing.T) {
	for _, step := range Flow() {
		if step.ID == "" || step.TitleKey == "" {
			t.Errorf("step %+v is missing an id or title", step)
		}
		if len(step.Actions) == 0 || step.Actions[0].Style != "primary" {
			t.Errorf("step %s must start with a primary action", step.ID)
		}
		for _, field := range step.Fields {
			if field.Key == "" || field.Kind == "" {
				t.Errorf("step %s has an incomplete field %+v", step.ID, field)
			}
		}
	}
}

func TestOnlyOurStepsAreOwned(t *testing.T) {
	for _, step := range Flow() {
		owned := step.Kind == StepWelcome || step.Kind == StepTunnel
		if step.Owned != owned {
			t.Errorf("step %s: owned=%v, want %v — auth logic stays with Telegram", step.ID, step.Owned, owned)
		}
	}
}
