// src/pages/landing/LandingPage.tsx

import { LandingNav } from "./LandingNav";
import { HeroSection } from "./HeroSection";
import { FeaturesSection } from "./FeaturesSection";
import { AccountTypesSection } from "./AccountTypesSection";
import { WhyChooseUsSection } from "./WhyChooseUsSection";
import { LandingFooter } from "./LandingFooter";

export function LandingPage() {
  return (
    <div className="min-h-screen flex flex-col">
      <LandingNav />
      <main className="flex-1">
        <HeroSection />
        <FeaturesSection />
        <AccountTypesSection />
        <WhyChooseUsSection />
      </main>
      <LandingFooter />
    </div>
  );
}
