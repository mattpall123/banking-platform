import { Button } from "@/components/ui/button";

function App() {
  return (
    <div className="min-h-screen bg-background text-foreground flex items-center justify-center">
      <div className="text-center space-y-4">
        <h1 className="text-4xl font-bold">Banking Platform</h1>
        <p className="text-muted-foreground">Tailwind v4 + shadcn ready</p>
        <Button onClick={() => alert("Click works")}>Test button</Button>
      </div>
    </div>
  );
}

export default App;