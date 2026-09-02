package tunnel

import (
	"net"
	"strconv"
	"testing"
)

func TestResolvePortKeepsAFreeOne(t *testing.T) {
	port, err := resolvePort(0)
	if err != nil {
		t.Fatalf("resolvePort: %v", err)
	}
	if port <= 0 {
		t.Fatalf("port = %d", port)
	}

	same, err := resolvePort(port)
	if err != nil {
		t.Fatalf("resolvePort: %v", err)
	}
	if same != port {
		t.Errorf("a free port was replaced: asked %d, got %d", port, same)
	}
}

func TestResolvePortStepsAsideWhenTaken(t *testing.T) {
	// Hold a port the way another proxy app would.
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer listener.Close()
	taken := listener.Addr().(*net.TCPAddr).Port

	port, err := resolvePort(taken)
	if err != nil {
		t.Fatalf("resolvePort: %v", err)
	}
	if port == taken {
		t.Fatalf("resolvePort handed back the busy port %d", taken)
	}

	// Whatever it picked has to be bindable, or the core would fail to start.
	check, err := net.Listen("tcp", net.JoinHostPort("127.0.0.1", strconv.Itoa(port)))
	if err != nil {
		t.Fatalf("the replacement port %d is not usable: %v", port, err)
	}
	_ = check.Close()
}
