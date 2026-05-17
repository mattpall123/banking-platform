// src/pages/landing/WhyChooseUsSection.tsx

const stats = [
  { value: "100%", label: "Canadian", sub: "Federally incorporated" },
  { value: "$100K", label: "CDIC Insured", sub: "Per eligible category" },
  { value: "256-bit", label: "Encryption", sub: "End-to-end security" },
  { value: "3", label: "Account Types", sub: "Chequing, Savings, TFSA" },
];

export function WhyChooseUsSection() {
  return (
    <section id="why-us" className="py-20 bg-primary text-primary-foreground">
      <div className="max-w-6xl mx-auto px-6">
        <div className="text-center mb-14">
          <h2 className="text-3xl md:text-4xl font-bold mb-4">Why MapleBank?</h2>
          <p className="text-primary-foreground/70 text-lg max-w-xl mx-auto">
            Built from the ground up with transparency, security, and Canadian values at the core.
          </p>
        </div>
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-8">
          {stats.map(({ value, label, sub }) => (
            <div key={label} className="text-center">
              <div className="text-4xl md:text-5xl font-bold mb-2">{value}</div>
              <div className="font-semibold text-lg mb-1">{label}</div>
              <div className="text-sm text-primary-foreground/60">{sub}</div>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
