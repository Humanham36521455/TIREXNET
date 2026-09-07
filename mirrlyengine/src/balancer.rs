use once_cell::sync::Lazy;
use parking_lot::RwLock;
use rand::seq::SliceRandom;
use std::collections::HashMap;

pub const STABILITY_HYSTERESIS_MS: u64 = 60;

pub struct Balancer {
    domains: Vec<String>,
    dc_rankings: HashMap<i32, Vec<(String, u64)>>,
    dc_to_domain: HashMap<i32, String>,
}

pub static BALANCER: Lazy<RwLock<Balancer>> = Lazy::new(|| RwLock::new(Balancer::new()));

impl Balancer {
    pub fn new() -> Self {
        Self {
            domains: Vec::new(),
            dc_rankings: HashMap::new(),
            dc_to_domain: HashMap::new(),
        }
    }

    pub fn update_domains_list(&mut self, domains_list: &[String]) {
        let mut current_sorted = self.domains.clone();
        current_sorted.sort();
        let mut new_sorted = domains_list.to_vec();
        new_sorted.sort();

        if current_sorted == new_sorted {
            return;
        }

        self.domains = domains_list.to_vec();
        if self.dc_rankings.is_empty() {
            let mut rng = rand::thread_rng();
            self.dc_to_domain.clear();
            for dc_id in [1, 2, 3, 4, 5, 203] {
                if let Some(domain) = self.domains.choose(&mut rng) {
                    self.dc_to_domain.insert(dc_id, domain.clone());
                }
            }
        }
    }

    pub fn update_ranked_domains_for_dc(&mut self, dc_id: i32, mut ranked: Vec<(String, u64)>) {
        if ranked.is_empty() {
            return;
        }
        ranked.sort_by_key(|(_, latency)| *latency);
        let ranked_map: HashMap<&str, u64> =
            ranked.iter().map(|(d, l)| (d.as_str(), *l)).collect();
        let (best_domain, best_latency) = &ranked[0];

        let should_switch = match self.dc_to_domain.get(&dc_id) {
            Some(current_d) if !current_d.is_empty() => {
                match ranked_map.get(current_d.as_str()) {
                    Some(&current_lat) => {
                        current_lat > best_latency + STABILITY_HYSTERESIS_MS
                    }
                    None => true,
                }
            }
            _ => true,
        };

        if should_switch {
            self.dc_to_domain.insert(dc_id, best_domain.clone());
        }

        self.dc_rankings.insert(dc_id, ranked);
    }

    pub fn update_ranked_domains(&mut self, ranked: Vec<(String, u64)>) {
        self.update_ranked_domains_for_dc(2, ranked);
    }

    pub fn update_domain_for_dc(&mut self, dc_id: i32, domain: &str) -> bool {
        if self.dc_to_domain.get(&dc_id).map(|s| s.as_str()) == Some(domain) {
            return false;
        }
        self.dc_to_domain.insert(dc_id, domain.to_string());
        true
    }

    pub fn get_active_domain_for_dc(&self, dc_id: i32) -> Option<String> {
        self.dc_to_domain.get(&dc_id).filter(|s| !s.is_empty()).cloned()
    }

    pub fn get_fastest_domain_for_dc(&self, dc_id: i32) -> Option<String> {
        self.dc_rankings
            .get(&dc_id)
            .and_then(|r| r.first())
            .map(|(d, _)| d.clone())
            .or_else(|| {
                self.dc_rankings
                    .get(&2)
                    .and_then(|r| r.first())
                    .map(|(d, _)| d.clone())
            })
    }

    pub fn get_fastest_domain(&self) -> Option<String> {
        self.get_fastest_domain_for_dc(2)
    }

    pub fn get_domains_for_dc(&self, dc_id: i32) -> Vec<String> {
        let mut result = Vec::new();
        let mut seen = std::collections::HashSet::new();

        if let Some(d) = self.dc_to_domain.get(&dc_id) {
            if !d.is_empty() {
                result.push(d.clone());
                seen.insert(d.clone());
            }
        }

        if let Some(ranked) = self.dc_rankings.get(&dc_id) {
            for (d, _) in ranked {
                if !seen.contains(d) {
                    result.push(d.clone());
                    seen.insert(d.clone());
                }
            }
        } else if let Some(dc2_ranked) = self.dc_rankings.get(&2) {
            for (d, _) in dc2_ranked {
                if !seen.contains(d) {
                    result.push(d.clone());
                    seen.insert(d.clone());
                }
            }
        }

        let mut remaining = self.domains.clone();
        let mut rng = rand::thread_rng();
        remaining.shuffle(&mut rng);

        for d in remaining {
            if !seen.contains(&d) {
                result.push(d.clone());
                seen.insert(d);
            }
        }

        result
    }

    pub fn record_latency_for_dc(&mut self, dc_id: i32, domain: &str, latency_ms: u64) {
        if domain.is_empty() {
            return;
        }
        let list = self.dc_rankings.entry(dc_id).or_default();
        if let Some(pos) = list.iter().position(|(d, _)| d == domain) {
            let old_lat = list[pos].1;
            let smoothed = (old_lat * 7 + latency_ms * 3) / 10;
            list[pos].1 = smoothed;
        } else {
            list.push((domain.to_string(), latency_ms));
        }
        list.sort_by_key(|(_, l)| *l);
    }

    pub fn reset_ranking(&mut self) {
        self.dc_rankings.clear();
        self.dc_to_domain.clear();
        if !self.domains.is_empty() {
            let mut rng = rand::thread_rng();
            for dc_id in [1, 2, 3, 4, 5, 203] {
                if let Some(domain) = self.domains.choose(&mut rng) {
                    self.dc_to_domain.insert(dc_id, domain.clone());
                }
            }
        }
    }
      }
