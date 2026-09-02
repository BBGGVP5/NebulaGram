// Package onboarding describes the first-run flow: the welcome screen, the
// optional NebulaLink setup and the hand-off to Telegram's own login.
//
// Like settings.Menu, the flow is data rather than code, so Android, iOS and
// desktop render the very same steps with native widgets and the copy lives in
// one place.
//
// Only the welcome and tunnel steps belong to us. Phone number, code and
// two-step password are driven by Telegram's own auth state machine — we
// describe how they look, never what they do. See docs/DESIGN.md for why.
package onboarding

// StepKind tells the client which screen to build.
type StepKind string

const (
	// StepWelcome is the branded first screen with a single primary button.
	StepWelcome StepKind = "welcome"
	// StepTunnel offers to paste a subscription before logging in. Without a
	// working tunnel the login SMS may never arrive on a censored network, so
	// this deliberately comes before the phone number.
	StepTunnel StepKind = "tunnel"
	// StepPhone, StepCode and StepPassword are rendered by us but driven by
	// Telegram's auth flow.
	StepPhone    StepKind = "phone"
	StepCode     StepKind = "code"
	StepPassword StepKind = "password"
)

// Action is a button on a step.
type Action struct {
	Key      string `json:"key"`
	TitleKey string `json:"title_key"`
	Title    string `json:"title"`
	// Style is "primary", "tonal" or "text", mapped to the platform's own
	// button styles (Material 3 filled/tonal/text, iOS glass/plain).
	Style string `json:"style"`
	// Command is a core method for steps we own; empty means the client
	// advances the flow itself.
	Command string `json:"command,omitempty"`
	// Skippable marks the action that leaves a step without completing it.
	Skippable bool `json:"skippable,omitempty"`
}

// Step is one screen of the first-run flow.
type Step struct {
	Kind        StepKind `json:"kind"`
	ID          string   `json:"id"`
	TitleKey    string   `json:"title_key"`
	Title       string   `json:"title"`
	SubtitleKey string   `json:"subtitle_key,omitempty"`
	Subtitle    string   `json:"subtitle,omitempty"`
	// Art names the illustration or animation the client shows above the copy.
	Art string `json:"art,omitempty"`
	// Fields are the inputs on the step, in order.
	Fields []Field `json:"fields,omitempty"`
	// Actions are the buttons, primary one first. Clients pin them to the
	// bottom of the screen.
	Actions []Action `json:"actions"`
	// Owned marks the steps whose behaviour is ours rather than Telegram's.
	Owned bool `json:"owned"`
}

// FieldKind is the input widget of a step field.
type FieldKind string

const (
	FieldPhone    FieldKind = "phone"
	FieldCode     FieldKind = "code"
	FieldPassword FieldKind = "password"
	FieldText     FieldKind = "text"
)

// Field is one input on a step.
type Field struct {
	Key         string    `json:"key"`
	Kind        FieldKind `json:"kind"`
	HintKey     string    `json:"hint_key"`
	Hint        string    `json:"hint"`
	Length      int       `json:"length,omitempty"` // fixed-length code boxes
	Optional    bool      `json:"optional,omitempty"`
	SecureEntry bool      `json:"secure_entry,omitempty"`
}

// Flow returns the first-run steps in order.
func Flow() []Step {
	return []Step{welcome(), tunnel(), phone(), code(), password()}
}

func welcome() Step {
	return Step{
		Kind: StepWelcome, ID: "onboarding.welcome", Owned: true,
		TitleKey: "ob_welcome_title", Title: "Welcome to NebulaGram",
		SubtitleKey: "ob_welcome_subtitle",
		Subtitle:    "A Telegram client with a built-in tunnel: your servers, your keys, no third-party relays.",
		Art:         "nebula_logo",
		Actions: []Action{
			{Key: "next", TitleKey: "ob_next", Title: "Next", Style: "primary"},
			{Key: "language", TitleKey: "ob_language", Title: "Change language", Style: "text"},
		},
	}
}

func tunnel() Step {
	return Step{
		Kind: StepTunnel, ID: "onboarding.tunnel", Owned: true,
		TitleKey: "ob_tunnel_title", Title: "Connect NebulaLink",
		SubtitleKey: "ob_tunnel_subtitle",
		Subtitle:    "Paste a subscription link or a server key. On a censored network the login code will not arrive without it.",
		Art:         "nebula_shield",
		Fields: []Field{{
			Key: "subscription", Kind: FieldText, Optional: true,
			HintKey: "ob_tunnel_hint", Hint: "Subscription link or vless:// key",
		}},
		Actions: []Action{
			{Key: "connect", TitleKey: "ob_connect", Title: "Connect", Style: "primary", Command: "onboarding.connect"},
			{Key: "skip", TitleKey: "ob_skip", Title: "Skip for now", Style: "text", Skippable: true},
		},
	}
}

func phone() Step {
	return Step{
		Kind: StepPhone, ID: "onboarding.phone",
		TitleKey: "ob_phone_title", Title: "Your phone number",
		SubtitleKey: "ob_phone_subtitle", Subtitle: "Confirm your country code and enter your number.",
		Art: "nebula_phone",
		Fields: []Field{
			{Key: "country", Kind: FieldText, HintKey: "ob_country", Hint: "Country"},
			{Key: "phone", Kind: FieldPhone, HintKey: "ob_phone", Hint: "Phone number"},
		},
		Actions: []Action{{Key: "next", TitleKey: "ob_next", Title: "Next", Style: "primary"}},
	}
}

func code() Step {
	return Step{
		Kind: StepCode, ID: "onboarding.code",
		TitleKey: "ob_code_title", Title: "Enter the code",
		SubtitleKey: "ob_code_subtitle", Subtitle: "We have sent you a confirmation code.",
		Art:    "nebula_code",
		Fields: []Field{{Key: "code", Kind: FieldCode, Length: 5, HintKey: "ob_code", Hint: "Code"}},
		Actions: []Action{
			{Key: "next", TitleKey: "ob_next", Title: "Next", Style: "primary"},
			{Key: "resend", TitleKey: "ob_resend", Title: "Did not get the code?", Style: "text"},
		},
	}
}

func password() Step {
	return Step{
		Kind: StepPassword, ID: "onboarding.password",
		TitleKey: "ob_password_title", Title: "Two-step verification",
		SubtitleKey: "ob_password_subtitle", Subtitle: "Enter your cloud password to finish signing in.",
		Art: "nebula_lock",
		Fields: []Field{{
			Key: "password", Kind: FieldPassword, SecureEntry: true,
			HintKey: "ob_password", Hint: "Password",
		}},
		Actions: []Action{
			{Key: "next", TitleKey: "ob_next", Title: "Next", Style: "primary"},
			{Key: "forgot", TitleKey: "ob_forgot", Title: "Forgot password?", Style: "text"},
		},
	}
}
